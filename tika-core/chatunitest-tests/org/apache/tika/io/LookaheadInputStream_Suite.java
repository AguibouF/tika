package org.apache.tika.io;

import org.junit.runner.RunWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.SelectClasses;

@RunWith(value = JUnitPlatform.class)
@SelectClasses(value = { LookaheadInputStream_markSupported_6_0_Test.class, LookaheadInputStream_read_3_0_Test.class })
public class LookaheadInputStream_Suite {
}
