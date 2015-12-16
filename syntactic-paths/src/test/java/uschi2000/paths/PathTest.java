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

package uschi2000.paths;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class PathTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_constructorNormalizes() {
        assertThat(new Path("a//b"), is(new Path("a/b")));
    }

    @Test
    public void testConstructor_illegalCharacters() {
        String path = new String(new char[] {'a', 0, 'b'});
        try {
            new Path(path);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Path contains illegal characters: " + path));
        }
    }

    @Test
    public void testConstructor_illegalSegments() {
        for (String illegal : new String[] {".", ".."}) {
            String path = "a/" + illegal + "/b";
            try {
                new Path(path);
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage(), is("Path contains illegal segments: [a, " + illegal + ", b]"));
            }
        }
    }

    @Test
    public void testConstructor_normalizes() {
        assertThat(new Path("a//b").toString(), is("a/b"));
        assertThat(new Path("a/").toString(), is("a"));
    }

    @Test
    public void testToAbsolutePath() {
        Path path = new Path("a/b").toAbsolutePath();
        assertTrue(path.isAbsolute());
        assertThat(path, is(new Path("/a/b")));
    }

    @Test
    public void testRelativize() {
        assertThat(Path.ROOT_PATH.relativize(new Path("/a/b")), is(new Path("a/b")));
        assertThat(new Path("/a").relativize(new Path("/a/b")), is(new Path("b")));
        assertThat(new Path("/a/b/c").relativize(new Path("/a/b/c/d/e/f")), is(new Path("d/e/f")));
        assertThat(new Path("a/b/c").relativize(new Path("a/b/c/d/e/f")), is(new Path("d/e/f")));
        assertThat(new Path("a/b/c").relativize(new Path("a/b/c")), is(new Path("")));

        assertThat(new Path("/a/b/c").relativize("/a/b/c/d/e/f"), is(new Path("d/e/f")));
    }

    @Test
    public void testRelativize_differentPaths() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Cannot relativize absolute vs relative path: /a vs a/b");
        new Path("/a").relativize(new Path("a/b"));
    }

    @Test
    public void testRelativize_noPrefix() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                "Relativize requires this path to be a prefix of the other path: /a/b/c vs /a/b/d");
        new Path("/a/b/c").relativize(new Path("/a/b/d"));
    }

    @Test
    public void test_utf8() {
        assertThat(new Path("/a/¡"), is(new Path("/a/¡")));
    }

    @Test
    public void testGetParent() {
        assertThat(new Path("/a/b/c").getParent(), is(new Path("/a/b")));
        assertThat(new Path("/a/b/c/").getParent(), is(new Path("/a/b")));
        assertThat(new Path("a/b/c").getParent(), is(new Path("a/b")));
        assertThat(new Path("a/b/c/").getParent(), is(new Path("a/b")));
        assertThat(new Path("/a").getParent(), is(Path.ROOT_PATH));
        assertNull(new Path("a").getParent());
        assertNull(new Path("/").getParent());
    }

    @Test
    public void testResolve() {
        assertThat(new Path("/a").resolve(new Path("b")), is(new Path("/a/b")));
        assertThat(new Path("/a").resolve(new Path("/b")), is(new Path("/b")));
        assertThat(new Path("/a/b").resolve(new Path("/c/d")), is(new Path("/c/d")));
        assertThat(new Path("a").resolve(new Path("b")), is(new Path("a/b")));

        assertThat(new Path("/a/b").resolve("/c/d"), is(new Path("/c/d")));
    }

    @Test
    public void testGetRoot() {
        assertThat(new Path("/a").getRoot(), is(Path.ROOT_PATH));
        assertNull(new Path("a").getRoot());
    }

    @Test
    public void testStartsWith() {
        assertTrue(new Path("a/b").startsWith(new Path("a")));
        assertTrue(new Path("/a/b").startsWith(new Path("/a")));
        assertTrue(new Path("/a/b").startsWith(new Path("/a/b")));

        assertFalse(new Path("/a/b").startsWith(new Path("a")));
        assertFalse(new Path("/a/b").startsWith(new Path("/a/b/c")));
    }

    @Test
    public void testEndsWith() {
        assertTrue(new Path("/a/b").endsWith(new Path("b")));
        assertTrue(new Path("a/b").endsWith(new Path("b")));
        assertTrue(new Path("a/b").endsWith(new Path("a/b")));
        assertTrue(new Path("/a/b").endsWith(new Path("/a/b")));
        assertTrue(new Path("/a/b").endsWith(new Path("a/b")));

        assertFalse(new Path("a/b").endsWith(new Path("/a/b")));
        assertFalse(new Path("/a/b").endsWith(new Path("/b")));
        assertFalse(new Path("/a/b").endsWith(new Path("/a/b/c")));
    }

    @Test
    public void testGetFileName() {
        assertThat(new Path("/a/b/").getFileName(), is(new Path("b")));
        assertThat(new Path("/a/b").getFileName(), is(new Path("b")));
        assertThat(new Path("/a/").getFileName(), is(new Path("a")));
        Path path = new Path("a");
        assertSame(path.getFileName(), path);
    }
}
