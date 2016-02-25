/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.util.syntacticpath;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import java.util.Iterator;

/** Factory methods for {@link Path}s. */
public final class Paths {
    private Paths() {}


    /**
     * Constructs a new {@link Path} from the given segments by joining the segments by {@link Path#SEPARATOR path
     * separator "/"} and invoking {@link Path(String)}. The returned path is {@link Path#isAbsolute() absolute} iff the
     * first non-blank segment starts with the {@link Path#SEPARATOR path separator "/"}.
     */
    public static Path get(String... segments) {
        Iterator<String> nonBlankSegments = Iterators.filter(Iterators.forArray(segments), new Predicate<String>() {
            @Override
            public boolean apply(String segment) {
                return !Strings.isNullOrEmpty(segment);
            }
        });
        return new Path(Path.PATH_JOINER.join(nonBlankSegments));
    }
}
