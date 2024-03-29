/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.logsafe.exceptions.SafeNullPointerException;
import org.junit.jupiter.api.Test;

public final class PathsTest {

    @Test
    public void test_singleElement() {
        assertThat(Paths.get("")).isEqualTo(new Path(""));
        assertThat(Paths.get("/")).isEqualTo(new Path("/"));
        assertThat(Paths.get("a")).isEqualTo(new Path("a"));
        assertThat(Paths.get("/a")).isEqualTo(new Path("/a"));
        assertThat(Paths.get("/a/")).isEqualTo(new Path("/a/"));
        assertThat(Paths.get("/a//b//c"))
                .isEqualTo(new Path("/a/b/c"))
                .isEqualTo(Paths.get("//a//b//c").normalize())
                .isEqualTo(Paths.get("/a/b/c"));
    }

    @Test
    public void test_singleElement_null() {
        assertThatLoggableExceptionThrownBy(() -> Paths.get((String) null))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessage("path cannot be null")
                .hasNoArgs();

        assertThatLoggableExceptionThrownBy(() -> Paths.get((String) null))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessage("path cannot be null")
                .hasNoArgs();

        assertThatLoggableExceptionThrownBy(() -> new Path(null))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessage("path cannot be null")
                .hasNoArgs();
    }

    @Test
    public void test_singleElement_null_array() {
        String[] segments = null;
        assertThatLoggableExceptionThrownBy(() -> Paths.get(segments))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessage("segments cannot be null")
                .hasNoArgs();
    }

    @Test
    public void test_multipleElements() {
        assertThat(Paths.get()).isEqualTo(new Path(""));
        assertThat(Paths.get("a", "")).isEqualTo(new Path("a"));
        assertThat(Paths.get("a", null)).isEqualTo(new Path("a"));
        assertThat(Paths.get("a", "", "b")).isEqualTo(new Path("a/b"));
        assertThat(Paths.get("a", null, "b")).isEqualTo(new Path("a/b"));
        assertThat(Paths.get("", "b")).isEqualTo(new Path("b"));
        assertThat(Paths.get("", "", "b")).isEqualTo(new Path("b"));
        assertThat(Paths.get("", "", "/b")).isEqualTo(new Path("/b"));
        assertThat(Paths.get("", "", "/", "b")).isEqualTo(new Path("/b"));
        assertThat(Paths.get("a", "b")).isEqualTo(new Path("a/b"));
        assertThat(Paths.get("a", "/")).isEqualTo(new Path("a/"));
        assertThat(Paths.get("/a", "b")).isEqualTo(new Path("/a/b"));
        assertThat(Paths.get("/a", "b", "c", "d")).isEqualTo(new Path("/a/b/c/d"));
        assertThat(Paths.get("/a", "/b", "/c", "/d", "/")).isEqualTo(new Path("/a/b/c/d/"));
        assertThat(Paths.get("/a", "/b", "//", "/c")).isEqualTo(new Path("/a/b/c"));
    }
}
