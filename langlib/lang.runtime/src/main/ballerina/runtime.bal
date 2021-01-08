// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/java;
import ballerina/lang.'array as lang_array;
import ballerina/lang.'value as lang_value;

# A listener that is dynamically registered with a module.
public type DynamicListener object {
    public function 'start() returns error?;
    public function gracefulStop() returns error?;
    public function immediateStop() returns error?;
};

# Represents a data holder of the current call stack element.
#
# + callableName - Callable name
# + moduleName - Module name
# + fileName - File name
# + lineNumber - Line number
public type CallStackElement record {|
    string callableName;
    string moduleName;
    string fileName;
    int lineNumber;
|};

# Register a listener object with a module.
# + listener - the listener object to be registered. The listener becomes a module listener of the module from which
#       this function is called.
public function registerListener(DynamicListener 'listener) = @java:Method {
    'class: "org.ballerinalang.langlib.runtime.Registry"
} external;

# Deregister a listener from a module.
# + listener - the listener object to be unregistered. The `listener` ceases to be a module listener of the module from
# which this function is called.
public function deregisterListener(DynamicListener 'listener) = @java:Method {
    'class: "org.ballerinalang.langlib.runtime.Registry"
} external;

# Halts the current strand for a predefined amount of time.
#
# + seconds - An amount of time to sleep in seconds
public isolated function sleep(decimal seconds) = @java:Method {
    'class: "org.ballerinalang.langlib.runtime.Sleep"
} external;

# Returns a stack trace for the current call stack.
#
# + return - An array representing the current call stack
public isolated function getStackTrace() returns StackFrame[] {
    StackFrame[] stackFrame = [];
    int i = 0;
    CallStackElement[] callStackElements = externGetStackTrace();
    lang_array:forEach(callStackElements, isolated function (CallStackElement callStackElement) {
                        stackFrame[i] = new StackFrameImpl(callStackElement.callableName,
                        callStackElement.moduleName, callStackElement.fileName, callStackElement.lineNumber);
                        i += 1;
                        });
    return stackFrame;
}

# Type representing a stack frame.
# A call stack is represented as an array of stack frames.
public type StackFrame readonly & object {
   # Returns a string representing this StackFrame.
   #
   # + return - A StackFrame as string
   public function toString() returns string;
};

# Represent a stack frame.
public readonly class StackFrameImpl {

    *StackFrame;
    public string callableName;
    public string moduleName;
    public string fileName;
    public int lineNumber;

    # Returns a string representing this StackFrame
    #
    # + return - A string
    public function toString() returns string {
        return "callableName: " + self.callableName + " " + "moduleName: " + self.moduleName +
        " " + "fileName: " + self.fileName + " " + "lineNumber: " + lang_value:toString(self.lineNumber);
    }

    public function init(string callableName, string moduleName, string fileName, int lineNumber) {
        self.callableName = callableName;
        self.moduleName = moduleName;
        self.fileName = fileName;
        self.lineNumber = lineNumber;
    }
}

isolated function externGetStackTrace() returns CallStackElement[] = @java:Method {
    name: "getStackTrace",
    'class: "org.ballerinalang.langlib.runtime.GetStackTrace"
} external;
