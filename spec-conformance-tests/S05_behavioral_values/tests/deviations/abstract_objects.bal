// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/test;

// object-type-descriptor :=
// object-type-quals object { object-member-descriptor* }
// object-type-quals := [abstract] [client] | [client] abstract
// object-member-descriptor := object-field-descriptor | object-method | object-type-reference
// TODO: Keyword abstract should be allowed after client keyword
// https://github.com/ballerina-platform/ballerina-lang/issues/13346
//type AbstractObject client abstract object {
//
//};

// An abstract object type must not have an object-ctor-function and does
// not have an implicit initial value.
// TODO: Abstract objects have should have no implicit initial value.
@test:Config {}
function testImplicitInitialValueOfAbstractObjectBroken() {
    AbstractObject[] abstractObjectArray = []; // Should fail at compile time
}

// An abstract object type descriptor must not contain a method-defn.
// TODO: Usage of extern keyword within abstract methods should be restricted.
// https://github.com/ballerina-platform/ballerina-lang/issues/13344
//type AbstractObjectBroken abstract object {
//    extern function testField();
//};
