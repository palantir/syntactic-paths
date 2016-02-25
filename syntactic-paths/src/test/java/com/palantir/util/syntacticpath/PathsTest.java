/*
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

/*
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public final class PathsTest {

    @Test
    public void test_singleElement() {
        assertThat(Paths.get(""), is(new Path("")));
        assertThat(Paths.get("/"), is(new Path("/")));
        assertThat(Paths.get("a"), is(new Path("a")));
        assertThat(Paths.get("/a"), is(new Path("/a")));
        assertThat(Paths.get("/a/"), is(new Path("/a/")));
    }

    @Test
    public void test_multipleElements() {
        assertThat(Paths.get("", "b"), is(new Path("b")));
        assertThat(Paths.get("", "", "b"), is(new Path("b")));
        assertThat(Paths.get("", "", "/b"), is(new Path("/b")));
        assertThat(Paths.get("", "", "/", "b"), is(new Path("/b")));
        assertThat(Paths.get("a", "b"), is(new Path("a/b")));
        assertThat(Paths.get("a", "/"), is(new Path("a/")));
        assertThat(Paths.get("/a", "b"), is(new Path("/a/b")));
        assertThat(Paths.get("/a", "b", "c", "d"), is(new Path("/a/b/c/d")));
        assertThat(Paths.get("/a", "/b", "/c", "/d", "/"), is(new Path("/a/b/c/d/")));
        assertThat(Paths.get("/a", "/b", "//", "/c"), is(new Path("/a/b/c")));
    }
}
