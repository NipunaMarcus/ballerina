/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.diagnostic.properties;

import io.ballerina.tools.diagnostics.properties.DiagnosticProperty;
import io.ballerina.tools.diagnostics.properties.DiagnosticPropertyKind;

/**
 * Represents numeric-type constant-properties passed when diagnostic logging.
 *
 * @since Swan Lake
 */
public class BNumericProperty implements DiagnosticProperty<Number> {
    private final DiagnosticPropertyKind kind;
    private final Number value;

    public BNumericProperty(Number value) {
        this.kind = DiagnosticPropertyKind.NUMERIC;
        this.value = value;
    }

    @Override
    public DiagnosticPropertyKind kind() {
        return kind;
    }

    @Override
    public Number value() {
        return value;
    }
}
