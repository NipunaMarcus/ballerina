/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.testerina.core;

import com.google.gson.Gson;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ListConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.JarResolver;
import io.ballerina.projects.Module;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.testsuite.Test;
import io.ballerina.projects.testsuite.TestSuite;
import io.ballerina.projects.testsuite.TesterinaRegistry;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;

import org.ballerinalang.test.runtime.util.TesterinaConstants;
import org.ballerinalang.util.diagnostic.DiagnosticLog;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolResolver;
import org.wso2.ballerinalang.compiler.semantics.analyzer.Types;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;

import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Responsible of processing Testerina annotations.
 * Lifetime of an instance of this class will end upon the completion of processing a ballerina package.
 */
public class TestAnnotationProcessor {
    private static final String TEST_ANNOTATION_NAME = "@test:Config";
    private static final String BEFORE_SUITE_ANNOTATION_NAME = "@test:BeforeSuite";
    private static final String AFTER_SUITE_ANNOTATION_NAME = "@test:AfterSuite";
    private static final String BEFORE_EACH_ANNOTATION_NAME = "@test:BeforeEach";
    private static final String AFTER_EACH_ANNOTATION_NAME = "@test:AfterEach";
    private static final String MOCK_ANNOTATION_NAME = "@test:Mock";
    private static final String BEFORE_FUNCTION = "before";
    private static final String AFTER_FUNCTION = "after";
    private static final String DEPENDS_ON_FUNCTIONS = "dependsOn";
    private static final String MODULE = "moduleName";
    private static final String FUNCTION = "functionName";
    private static final String GROUP_ANNOTATION_NAME = "groups";
    private static final String VALUE_SET_ANNOTATION_NAME = "dataProvider";
    private static final String TEST_ENABLE_ANNOTATION_NAME = "enable";
    private static final String AFTER_SUITE_ALWAYS_RUN_FIELD_NAME = "alwaysRun";
    private static final String VALUE_FIELD_NAME = "value";
    private static final String MOCK_ANNOTATION_DELIMITER = "#";
    private static final String MOCK_FN_DELIMITER = "~";
    private static final String BEFORE_GROUPS_ANNOTATION_NAME = "@test:BeforeGroups";
    private static final String AFTER_GROUPS_ANNOTATION_NAME = "@test:AfterGroups";

    private TesterinaRegistry registry = TesterinaRegistry.getInstance();
    private boolean enabled = true;
    private DiagnosticLog diagnosticLog;
    private Types typeChecker;
    private SymbolResolver symbolResolver;

    private PackageCache packageCache;
    private Map<BPackageSymbol, SymbolEnv> packageEnvironmentMap;

    /**
     * this property is used as a work-around to initialize test suites only once for a package as Compiler
     * Annotation currently emits package import events too to the process method.
     */

    public void init(CompilerContext compilerContext) {
        this.diagnosticLog = BLangDiagnosticLog.getInstance(compilerContext);
        this.packageEnvironmentMap = SymbolTable.getInstance(compilerContext).pkgEnvMap;
        this.packageCache = PackageCache.getInstance(compilerContext);
        this.typeChecker = Types.getInstance(compilerContext);
        this.symbolResolver = SymbolResolver.getInstance(compilerContext);

        if (TesterinaRegistry.getInstance().isTestSuitesCompiled()) {
            enabled = false;
        }
    }

    /**
     * Generate and return the testsuite for module tests.
     *
     * @param module module
     * @return test suite
     */
    public Optional<TestSuite> testSuite(Module module, Project project) {
        if (project.kind() != ProjectKind.SINGLE_FILE_PROJECT
                && !isTestable(module)) {
            return Optional.empty();
        }
        // skip generation of the testsuite if --skip-tests option is set to true
        if (project.buildOptions().skipTests()) {
            return Optional.empty();
        }

        return Optional.of(generateTestSuite(module, project));
    }

    private boolean isTestable(Module module){
        boolean isTestableFlag = !module.testDocumentIds().isEmpty();
        return isTestableFlag;
    }

    private TestSuite generateTestSuite(Module module, Project project) {
        TestSuite testSuite = new TestSuite(module.descriptor().name().toString(),
                module.descriptor().packageName().toString(),
                module.descriptor().org().value(), module.descriptor().version().toString());
        TesterinaRegistry.getInstance().getTestSuites().put(
                module.descriptor().name().toString(), testSuite);

        testSuite.setInitFunctionName(".<init>");
        testSuite.setStartFunctionName(".<start>");
        testSuite.setStopFunctionName(".<stop>");
        testSuite.setPackageName(module.descriptor().packageName().toString());
        testSuite.setSourceRootPath(project.sourceRoot().toString());
        //Get syntax tree for source files in the module
        Map<String, SyntaxTree> sourceSyntaxTreeMap = new HashMap<>();
        module.documentIds().forEach(documentId -> {
            Document document = module.document(documentId);
            sourceSyntaxTreeMap.put(document.name(), document.syntaxTree());
        });
        addUtilityFunctions(sourceSyntaxTreeMap, project, testSuite);
        Map<String, SyntaxTree> testSyntaxTreeMap;
        //set test init functions
        if (project.kind() == ProjectKind.SINGLE_FILE_PROJECT) {
            testSuite.setTestInitFunctionName(".<init>");
            testSuite.setTestStartFunctionName(".<start>");
            testSuite.setTestStopFunctionName(".<stop>");
            testSyntaxTreeMap = sourceSyntaxTreeMap;
        } else {
            //Get syntax tree for test files in the module
            testSyntaxTreeMap = new HashMap<>();
            module.testDocumentIds().forEach(documentId -> {
                Document document = module.document(documentId);
                testSyntaxTreeMap.put(document.name(), document.syntaxTree());
            });
            testSuite.setTestInitFunctionName(".<testinit>");
            testSuite.setTestStartFunctionName(".<teststart>");
            testSuite.setTestStopFunctionName(".<teststop>");
            addUtilityFunctions(testSyntaxTreeMap, project, testSuite);
        }
        // process annotations in test functions
        TestAnnotationProcessor testAnnotationProcessor = new TestAnnotationProcessor();
        CompilerContext compilerContext = project.projectEnvironmentContext().getService(CompilerContext.class);
        testAnnotationProcessor.init(compilerContext);
        processAnnotations(module, testSyntaxTreeMap);
        return testSuite;
    }

    private void processAnnotations(Module module, Map<String, SyntaxTree> syntaxTreeMap) {
        //Map to maintain all the mock functions
        Map<String, String> mockFunctionNamesMap = new HashMap<>();
        if (!enabled) {
            return;
        }
        String packageName = module.descriptor().packageName().toString();
        TestSuite suite = registry.getTestSuites().get(module.descriptor().name().toString());
        // Check if the registry contains a test suite for the package
        boolean isTestable = false;
        if(suite == null){
            //Set testable flag for single bal file execution
            if ((Names.DOT.getValue()).equals(packageName)) {
                isTestable =true;
            }else{
                isTestable = !module.testDocumentIds().isEmpty();
            }
            // Skip adding test suite if no tests are available in the tests path
            if (isTestable) {
                // Add a test suite to the registry if it does not contain one pertaining to the package name
                suite = registry.getTestSuites().computeIfAbsent(packageName, func ->
                        new TestSuite(module.descriptor().name().toString(),
                        module.descriptor().packageName().toString(),
                        module.descriptor().org().value(), module.descriptor().version().toString()));
            } else {
                return;
            }
        }
        for (Map.Entry<String, SyntaxTree> syntaxTreeEntry : syntaxTreeMap.entrySet()) {
            if (syntaxTreeEntry.getValue().containsModulePart()) {
                ModulePartNode modulePartNode = syntaxTreeMap.get(syntaxTreeEntry.getKey()).rootNode();
                for (Node node : modulePartNode.members()) {
                    if ((node.kind() == SyntaxKind.FUNCTION_DEFINITION) && node instanceof FunctionDefinitionNode) {
                        String functionName = ((FunctionDefinitionNode) node).functionName().text();
                        Optional<MetadataNode> optionalMetadataNode = ((FunctionDefinitionNode)node).metadata();
                        if (optionalMetadataNode.isEmpty()) {
                            continue;
                        }else{
                            NodeList<AnnotationNode> annotations = optionalMetadataNode.get().annotations();
                            for (AnnotationNode annotationNode : annotations) {
                                String annotationName = annotationNode.toString().trim();
                                if (annotationName.contains(BEFORE_SUITE_ANNOTATION_NAME)) {
                                    suite.addBeforeSuiteFunction(functionName);
                                } else if (annotationName.contains(AFTER_SUITE_ANNOTATION_NAME)) {
                                    suite.addAfterSuiteFunction(functionName, isAlwaysRunAfterSuite(annotationNode));
                                } else if (annotationName.contains(BEFORE_GROUPS_ANNOTATION_NAME)) {
                                    processGroupsAnnotation(annotationNode, functionName, suite, true);
                                } else if (annotationName.contains(AFTER_GROUPS_ANNOTATION_NAME)) {
                                    processGroupsAnnotation(annotationNode, functionName, suite, false);
                                } else if (annotationName.contains(BEFORE_EACH_ANNOTATION_NAME)) {
                                    suite.addBeforeEachFunction(functionName);
                                } else if (annotationName.contains(AFTER_EACH_ANNOTATION_NAME)) {
                                    suite.addAfterEachFunction(functionName);
                                } else if (annotationName.contains(TEST_ANNOTATION_NAME)) {
                                    processTestAnnotation(annotationNode, functionName, suite);
                                } else {
                                    // disregard this annotation
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void addUtilityFunctions(Map<String, SyntaxTree> syntaxTreeMap, Project project, TestSuite testSuite) {
        for (Map.Entry<String, SyntaxTree> syntaxTreeEntry : syntaxTreeMap.entrySet()) {
            if (syntaxTreeEntry.getValue().containsModulePart()) {
                ModulePartNode modulePartNode = syntaxTreeMap.get(syntaxTreeEntry.getKey()).rootNode();
                for (Node node : modulePartNode.members()) {
                    if ((node.kind() == SyntaxKind.FUNCTION_DEFINITION) && node instanceof FunctionDefinitionNode) {
                        //Add test utility functions
                        String functionName = ((FunctionDefinitionNode) node).functionName().text();
                        Location pos = node.location();
                        NodeList<Token> qualifiers = ((FunctionDefinitionNode) node).qualifierList();
                        boolean isUtility = true;
                        for (Token qualifier : qualifiers) {
                            if (Flag.RESOURCE.name().equals(qualifier.text()) ||
                                    Flag.REMOTE.name().equals(qualifier.text())) {
                                isUtility = false;
                                break;
                            }
                        }
                        if (pos != null && isUtility) {
                            // Remove the duplicated annotations.
                            String className = pos.lineRange().filePath().replace(".bal", "")
                                    .replace("/", ".");
                            String functionClassName = JarResolver.getQualifiedClassName(
                                    project.currentPackage().packageOrg().value(),
                                    project.currentPackage().packageName().value(),
                                    project.currentPackage().packageVersion().value().toString(),
                                    className);
                            testSuite.addTestUtilityFunction(functionName, functionClassName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Check whether there is a common element in two Lists.
     *
     * @param inputGroups    String @{@link List} to match
     * @param functionGroups String @{@link List} to match agains
     * @return true if a match is found
     */
    private boolean isGroupAvailable(List<String> inputGroups, List<String> functionGroups) {
        for (String group : inputGroups) {
            for (String funcGroup : functionGroups) {
                if (group.equals(funcGroup)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getStringValue(Node valueExpr){
        return valueExpr.toString().replaceAll("\\\"","").trim();
    }

    private String getModuleName(String orgName, String moduleName,String version){
        return orgName.concat(Names.ORG_NAME_SEPARATOR.value).concat(moduleName).
                concat(Names.VERSION_SEPARATOR.value).concat(version);
    }
    /**
     * Formats the package name obtained from the mock annotation.
     * Checks for empty, '.', or single module names and replaces them.
     * Ballerina modules and fully qualified packages are simply returned
     *
     * @param value package name
     * @return formatted package name
     */
    private String formatPackageName(String value, Module module) {
        // If empty or '.' then return the current package ID
        if (value.isEmpty() || value.equals(Names.DOT.value)) {
            value = PackageID.DEFAULT.toString();
            // If value does NOT contain 'ballerina/' then it could be fully qualified
        } else if (!value.contains(Names.ORG_NAME_SEPARATOR.value) && !value.contains(Names.VERSION_SEPARATOR.value)) {
            value = getPackageID(module, value).toString();
        }
        return value;
    }

    private PackageID getPackageID(Module module, String packageName) {
        PackageID packageID;
        // If empty or '.' then return the current package ID
        if (packageName.equals(Names.DOT.value)) {
            packageID = PackageID.DEFAULT;
            // If value does NOT contain 'ballerina/' then it could be fully qualified
        } else {
            packageID = new PackageID(new Name(module.descriptor().org().value()), new Name(packageName),
                    new Name(module.descriptor().version().toString()));
        }
        return packageID;
    }

    private FunctionDefinitionNode getFunctionDefinition(Module module, String functionName){
        FunctionDefinitionNode functionNode = null;
        for (DocumentId documentId: module.documentIds()) {
            Document document = module.document(documentId);
            ModulePartNode modulePartNode = document.syntaxTree().rootNode();
            for (Node node : modulePartNode.members()) {
                if ((node.kind() == SyntaxKind.FUNCTION_DEFINITION) && node instanceof FunctionDefinitionNode) {
                    String function = ((FunctionDefinitionNode) node).functionName().text();
                    if(functionName.equals(function)){
                        functionNode = (FunctionDefinitionNode) node;
                    }
                }
            }
        }
        return functionNode;
    }

    // Iterate through each field and assign the annotation values for moduleName and functionName
    private void setAnnotationValues(AnnotationNode annotationNode, String[] annotationValues, Module module) {
        Optional<MappingConstructorExpressionNode> mappingNodes = annotationNode.annotValue();
        if(!mappingNodes.isEmpty()) {
            for (MappingFieldNode mappingFieldNode : mappingNodes.get().fields()) {
                if (mappingFieldNode.kind() == SyntaxKind.SPECIFIC_FIELD) {
                    SpecificFieldNode specificField = (SpecificFieldNode) mappingFieldNode;
                    String fieldName = specificField.fieldName().toString().trim();
                    ExpressionNode valueExpr = specificField.valueExpr().orElse(null);
                    if(valueExpr!=null) {
                        String value = getStringValue(valueExpr);
                        if (MODULE.equals(fieldName)) {
                            value = formatPackageName(value, module);
                            annotationValues[0] = value;
                        } else if (FUNCTION.equals(fieldName)) {
                            annotationValues[1] = value;
                        }
                    }
                }else{
                    diagnosticLog.logDiagnostic(DiagnosticSeverity.ERROR, annotationNode.location(),
                            "Annotation fields must be key-value pairs");
                }
            }
        }
    }

    private AtomicBoolean isAlwaysRunAfterSuite(AnnotationNode annotationNode){
        AtomicBoolean alwaysRun = new AtomicBoolean(false);
        if(!annotationNode.annotValue().isEmpty()){
            Optional<MappingConstructorExpressionNode> mappingNodes = annotationNode.annotValue();
            if(!mappingNodes.isEmpty()){
                mappingNodes.get().fields().forEach(mappingFieldNode -> {
                    if(mappingFieldNode.kind() == SyntaxKind.SPECIFIC_FIELD){
                        SpecificFieldNode specificField = (SpecificFieldNode) mappingFieldNode;
                        if(AFTER_SUITE_ALWAYS_RUN_FIELD_NAME.equals(specificField.fieldName().toString().trim())){
                            ExpressionNode valueExpr = specificField.valueExpr().orElse(null);
                            if(valueExpr!=null){
                                if (Boolean.TRUE.toString().equals(valueExpr.toString())) {
                                    alwaysRun.set(true);
                                }
                            }
                        }
                    }
                });
            }
        }
        return alwaysRun;
    }

    private void processGroupsAnnotation(AnnotationNode annotationNode, String functionName, TestSuite suite, boolean isBeforeGroups){
        if(!annotationNode.annotValue().isEmpty()){
            Optional<MappingConstructorExpressionNode> mappingNodes = annotationNode.annotValue();
            if(!mappingNodes.isEmpty()){
                for (MappingFieldNode mappingFieldNode:mappingNodes.get().fields()) {
                    if(mappingFieldNode.kind() == SyntaxKind.SPECIFIC_FIELD){
                        SpecificFieldNode specificField = (SpecificFieldNode) mappingFieldNode;
                        if(VALUE_FIELD_NAME.equals(specificField.fieldName().toString().trim())){
                            ExpressionNode valueExpr = specificField.valueExpr().orElse(null);
                            if(SyntaxKind.LIST_CONSTRUCTOR == valueExpr.kind() && valueExpr instanceof ListConstructorExpressionNode){
                                List<String> groupList = new ArrayList<>();
                                ((ListConstructorExpressionNode) valueExpr).expressions().forEach(
                                        expression-> groupList.add(getStringValue(expression)));
                                if(isBeforeGroups){
                                    suite.addBeforeGroupsFunction(functionName, groupList);
                                }else {
                                    suite.addAfterGroupFunction(functionName, groupList);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private  void processTestAnnotation(AnnotationNode annotationNode, String functionName, TestSuite suite){
        Test test = new Test();
        test.setTestName(functionName);
        AtomicBoolean shouldSkip = new AtomicBoolean();
        AtomicBoolean groupsFound = new AtomicBoolean();
        List<String> groups = registry.getGroups();
        boolean shouldIncludeGroups = registry.shouldIncludeGroups();
        if(!annotationNode.annotValue().isEmpty()){
            Optional<MappingConstructorExpressionNode> mappingNodes = annotationNode.annotValue();
            if(!mappingNodes.isEmpty()) {
                for (MappingFieldNode mappingFieldNode : mappingNodes.get().fields()) {
                    if (mappingFieldNode.kind() == SyntaxKind.SPECIFIC_FIELD) {
                        SpecificFieldNode specificField = (SpecificFieldNode) mappingFieldNode;
                        String fieldName = specificField.fieldName().toString().trim();
                        ExpressionNode valueExpr = specificField.valueExpr().orElse(null);
                        if(valueExpr!=null) {
                            if (TEST_ENABLE_ANNOTATION_NAME.equals(fieldName)) {
                                if (Boolean.FALSE.toString().equals(getStringValue(valueExpr))) {
                                    shouldSkip.set(true);
                                    continue;
                                }
                            }
                            if (GROUP_ANNOTATION_NAME.equals(fieldName)) {
                                if (SyntaxKind.LIST_CONSTRUCTOR == valueExpr.kind() && valueExpr instanceof ListConstructorExpressionNode) {
                                    List<String> groupList = new ArrayList<>();
                                    ((ListConstructorExpressionNode) valueExpr).expressions().forEach(
                                            expression -> groupList.add(getStringValue(expression)));
                                    test.setGroups(groupList);
                                    suite.addTestToGroups(test);

                                    // Check whether user has provided a group list
                                    if (groups != null && !groups.isEmpty()) {
                                        boolean isGroupPresent = isGroupAvailable(groups, test.getGroups());
                                        if (shouldIncludeGroups) {
                                            // include only if the test belong to one of these groups
                                            if (!isGroupPresent) {
                                                // skip the test if this group is not defined in this test
                                                shouldSkip.set(true);
                                                continue;
                                            }
                                        } else {
                                            // exclude only if the test belong to one of these groups
                                            if (isGroupPresent) {
                                                // skip if this test belongs to one of the excluded groups
                                                shouldSkip.set(true);
                                                continue;
                                            }
                                        }
                                        groupsFound.set(true);
                                    }
                                }
                            }
                            if (VALUE_SET_ANNOTATION_NAME.equals(fieldName)) {
                                test.setDataProvider(getStringValue(valueExpr));
                            }
                            if (BEFORE_FUNCTION.equals(fieldName)) {
                                test.setBeforeTestFunction(getStringValue(valueExpr));
                            }
                            if (AFTER_FUNCTION.equals(fieldName)) {
                                test.setAfterTestFunction(getStringValue(valueExpr));
                            }
                            if (DEPENDS_ON_FUNCTIONS.equals(fieldName)) {
                                if (SyntaxKind.LIST_CONSTRUCTOR == valueExpr.kind() &&
                                        valueExpr instanceof ListConstructorExpressionNode) {
                                    List<String> dependsOnFunctions = new ArrayList<>();
                                    ((ListConstructorExpressionNode) valueExpr).expressions().forEach(
                                            expression-> dependsOnFunctions.add(getStringValue(expression)));
                                    for (String function:dependsOnFunctions) {
                                        test.addDependsOnTestFunction(function);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (groups != null && !groups.isEmpty() && !groupsFound.get() && shouldIncludeGroups) {
            // if the user has asked to run only a specific list of groups and this test doesn't have
            // that group, we should skip the test
            shouldSkip.set(true);
        }
        if (!shouldSkip.get()) {
            suite.addTests(test);
        }
    }

    /**
     * Write the content into a json.
     *
     * @param testSuite Data that are parsed to the json
     */
    private static void writeToJson(TestSuite testSuite, Path moduleTestsCachePath) throws IOException {
        if (!Files.exists(moduleTestsCachePath)) {
            Files.createDirectories(moduleTestsCachePath);
        }
        Path tmpJsonPath = Paths.get(moduleTestsCachePath.toString(), TesterinaConstants.TESTERINA_TEST_SUITE);
        File jsonFile = new File(tmpJsonPath.toString());
        Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8);
        Gson gson = new Gson();
        String json = gson.toJson(testSuite);
        writer.write(new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }
}
