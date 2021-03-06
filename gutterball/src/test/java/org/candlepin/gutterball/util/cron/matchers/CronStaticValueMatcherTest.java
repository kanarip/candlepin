/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package org.candlepin.gutterball.util.cron.matchers;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.util.Arrays;
import java.util.List;



/**
 * CronStaticValueMatcherTest
 */
@RunWith(JUnitParamsRunner.class)
public class CronStaticValueMatcherTest {

    private CronMatcher matcher;

    @Before
    public void setup() {
        this.matcher = new CronStaticValueMatcher(5);
    }

    @Test
    @Parameters(method = "parametersForTestMatches")
    public void testMatches(int input, boolean expected) {
        assertEquals(expected, this.matcher.matches(input));
    }

    public Object[] parametersForTestMatches() {
        List<Object[]> params = Arrays.asList(
            new Object[] { 5, true },
            new Object[] { 1, false },
            new Object[] { -5, false }
        );

        return params.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestHasNext")
    public void testHasNext(int input, boolean expected) {
        assertEquals(expected, this.matcher.hasNext(input));
    }

    public Object[] parametersForTestHasNext() {
        List<Object[]> params = Arrays.asList(
            new Object[] { 1, true },
            new Object[] { 3, true },
            new Object[] { 5, true },
            new Object[] { 7, false },
            new Object[] { 9, false }
        );

        return params.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestNext")
    public void testNext(int input, int expected) {
        assertEquals(expected, this.matcher.next(input));
    }

    public Object[] parametersForTestNext() {
        List<Object[]> params = Arrays.asList(
            new Object[] { 1, 5 },
            new Object[] { 3, 5 },
            new Object[] { 5, 5 },
            new Object[] { 7, 5 },
            new Object[] { 9, 5 }
        );

        return params.toArray();
    }

}
