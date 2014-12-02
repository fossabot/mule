/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.internal;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.module.http.internal.HttpParser.normalizePathWithSpaces;

import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import org.junit.Test;

@SmallTest
public class HttpParserTestCase extends AbstractMuleTestCase
{

    @Test
    public void normalizePath()
    {
        String expectedNormalizedPath = " some path";
        assertThat(normalizePathWithSpaces(expectedNormalizedPath), is(expectedNormalizedPath));
        assertThat(normalizePathWithSpaces("%20some%20path"), is(expectedNormalizedPath));
        assertThat(normalizePathWithSpaces("+some+path"), is(expectedNormalizedPath));
        assertThat(normalizePathWithSpaces("%20some+path"), is(expectedNormalizedPath));
        assertThat(normalizePathWithSpaces("+some%20path"), is(expectedNormalizedPath));
    }

}
