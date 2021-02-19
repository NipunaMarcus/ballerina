/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test.runtime.entity;

import io.ballerina.projects.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Coverage analysis of a specific module used to generate the Json object.
 *
 * @since 1.2.0
 */
public class ModuleCoverage {

    private String name;
    private int coveredLines;
    private int missedLines;
    private float coveragePercentage;
    private List<SourceFile> sourceFiles = new ArrayList<>();

    /**
     * Adds the code snippet from the source file highlighted with covered and missed lines.
     *
     * @param document     source file
     * @param coveredLines list of lines covered
     * @param missedLines  list of lines missed
     */
    public void addSourceFileCoverage(Document document, List<Integer> coveredLines,
                                      List<Integer> missedLines) {
        SourceFile sourceFile = new SourceFile(document, coveredLines, missedLines);
        this.sourceFiles.add(sourceFile);
        this.coveredLines += coveredLines.size();
        this.missedLines += missedLines.size();
        setCoveragePercentage();
    }

    /**
     * Check if given source file is already added to module coverage.
     *
     * @param fileName String
     * @return boolean
     */
    public boolean containsSourceFile(String fileName) {
        boolean isAvailable = false;
        for (SourceFile sourceFile : sourceFiles) {
            if (sourceFile.getName().equals(fileName)) {
                isAvailable = true;
                break;
            }
        }
        return isAvailable;
    }

    /**
     * Update coverage information for a given source file.
     *
     * @param document Document
     * @param coveredLines List<Integer>
     * @param missedLines List<Integer>
     */
    public void updateCoverage(Document document, List<Integer> coveredLines,
                               List<Integer> missedLines) {
        List<SourceFile> sourceFileList = new ArrayList<>(sourceFiles);
        for (SourceFile sourceFile : sourceFileList) {
            if (sourceFile.getName().equals(document.name())) {
                //Remove outdated source file details
                this.coveredLines -= sourceFile.coveredLines.size();
                this.missedLines -= sourceFile.missedLines.size();
                sourceFiles.remove(sourceFile);
                //Add updated source file details
                addSourceFileCoverage(document, coveredLines, missedLines);
            }
        }
    }

    private void setCoveragePercentage() {
        float coverageVal = (float) this.coveredLines / (this.coveredLines + this.missedLines) * 100;
        this.coveragePercentage = (float) (Math.round(coverageVal * 100.0) / 100.0);
    }

    public float getCoveragePercentage() {
        return coveragePercentage;
    }

    public int getCoveredLines() {
        return coveredLines;
    }

    public int getMissedLines() {
        return missedLines;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Get the missed lines list for a source file.
     *
     * @param sourceFileName String
     * @return list of missed lines
     */
    public List<Integer> getMissedLinesList(String sourceFileName) {
        for (SourceFile sourceFile : this.sourceFiles) {
            if (sourceFile.getName().equals(sourceFileName)) {
                return sourceFile.missedLines;
            }
        }
        return null;
    }

    /**
     * Get the covered lines list for a source file.
     *
     * @param sourceFileName String
     * @return list of covered lines
     */
    public List<Integer> getCoveredLinesList(String sourceFileName) {
        for (SourceFile sourceFile : this.sourceFiles) {
            if (sourceFile.getName().equals(sourceFileName)) {
                return sourceFile.coveredLines;
            }
        }
        return null;
    }


    /**
     * Inner class for the SourceFile in Json.
     */
    private static class SourceFile {
        private String name;
        private List<Integer> coveredLines;
        private List<Integer> missedLines;
        private float coveragePercentage;
        private String sourceCode;

        private SourceFile(Document document, List<Integer> coveredLines, List<Integer> missedLines) {
            this.name = document.name();
            this.coveredLines = coveredLines;
            this.missedLines = missedLines;
            setCoveragePercentage(coveredLines, missedLines);
            setSourceCode(document);
        }

        private void setCoveragePercentage(List<Integer> coveredLines, List<Integer> missedLines) {
            float coverageVal = (float) coveredLines.size() / (coveredLines.size() + missedLines.size()) * 100;
            this.coveragePercentage = (float) (Math.round(coverageVal * 100.0) / 100.0);
        }

        private void setSourceCode(Document document) {
            this.sourceCode = document.textDocument().toString();
        }

        public float getCoveragePercentage() {
            return this.coveragePercentage;
        }

        public String getSourceCode() {
            return this.sourceCode;
        }

        public List<Integer> getCoveredLines() {
            return this.coveredLines;
        }

        public List<Integer> getMissedLines() {
            return this.missedLines;
        }

        public String getName() {
            return name;
        }
    }
}
