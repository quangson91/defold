// Copyright 2020-2023 The Defold Foundation
// Copyright 2014-2020 King
// Copyright 2009-2014 Ragnar Svensson, Christian Murray
// Licensed under the Defold License version 1.0 (the "License"); you may not use
// this file except in compliance with the License.
// 
// You may obtain a copy of the License, together with FAQs at
// https://www.defold.com/license
// 
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.dynamo.bob.pipeline;

import java.util.ArrayList;
import java.util.List;

public class ResourceNode {

    public final String relativeFilepath;
    public final String absoluteFilepath;
    private ResourceNode parent = null;
    private final List<ResourceNode> children = new ArrayList<ResourceNode>();

    public ResourceNode(final String relativeFilepath, final String absoluteFilepath) {
        if (relativeFilepath.startsWith("/")) {
            this.relativeFilepath = relativeFilepath;
        } else {
            this.relativeFilepath = "/" + relativeFilepath;
        }
        this.absoluteFilepath = absoluteFilepath;
    }

    public void addChild(ResourceNode childNode) {
        childNode.parent = this;
        this.children.add(childNode);
    }

    public List<ResourceNode> getChildren() {
        return this.children;
    }

    public ResourceNode getParent() {
        return this.parent;
    }
}