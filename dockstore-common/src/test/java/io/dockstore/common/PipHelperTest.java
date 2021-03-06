package io.dockstore.common;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 14/08/18
 */
public class PipHelperTest {
    @Test
    public void convertSemVerToAvailableVersion() throws Exception {
        Assert.assertEquals("1.5.0", PipHelper.convertSemVerToAvailableVersion(PipHelper.DEV_SEM_VER));
        Assert.assertEquals("1.5.0", PipHelper.convertSemVerToAvailableVersion(null));
        Assert.assertEquals("1.5.0", PipHelper.convertSemVerToAvailableVersion("1.5.0-snapshot"));
        Assert.assertEquals("1.5.0", PipHelper.convertSemVerToAvailableVersion("1.5.0"));
        Assert.assertEquals("1.4.0", PipHelper.convertSemVerToAvailableVersion("1.4.0-snapshot"));
        Assert.assertEquals("1.4.0", PipHelper.convertSemVerToAvailableVersion("1.4.0"));
        Assert.assertEquals("1.4.0", PipHelper.convertSemVerToAvailableVersion("1.3.0-snapshot"));
        Assert.assertEquals("1.4.0", PipHelper.convertSemVerToAvailableVersion("1.3.0"));
    }
}
