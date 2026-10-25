/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.interop;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed tree node of the interop vectors, used for parent references. The oracle registers the class alias org.red5.io.interop.InteropNode as
 * dynamic with the sealed members children, name and parent.
 */
public class InteropNode {

    public List<Object> children = new ArrayList<>();

    public String name;

    public InteropNode parent;

    public InteropNode() {
    }

    public InteropNode(String name) {
        this.name = name;
    }

}
