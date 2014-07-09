/*
 * Copyright (C) 2014 Red Hat, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Madhu Venugopal, Dave Tucker
 */

package org.opendaylight.ovsdb.schema.openvswitch;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

import org.junit.Before;
import org.opendaylight.ovsdb.lib.OvsdbClient;
import org.opendaylight.ovsdb.lib.OvsdbConnection;
import org.opendaylight.ovsdb.lib.OvsdbConnectionListener;
import org.opendaylight.ovsdb.lib.impl.OvsdbConnectionService;
import org.opendaylight.ovsdb.lib.message.OvsdbRPC;
import org.opendaylight.ovsdb.lib.notation.Row;
import org.opendaylight.ovsdb.lib.notation.UUID;

import com.google.common.util.concurrent.ListenableFuture;

public abstract class OpenVswitchSchemaTestBase implements OvsdbRPC.Callback{
    private final static String SERVER_IPADDRESS = "ovsdbserver.ipaddress";
    private final static String SERVER_PORT = "ovsdbserver.port";
    private final static String CONNECTION_TYPE = "ovsdbserver.connection";
    private final static String CONNECTION_TYPE_ACTIVE = "active";
    private final static String CONNECTION_TYPE_PASSIVE = "passive";
    private final static String DEFAULT_SERVER_PORT = "6640";
    protected static final String TEST_BRIDGE_NAME = "br_test";
    /**
     * Represents the Open vSwitch Schema
     */
    protected final static String OPEN_VSWITCH_SCHEMA = "Open_vSwitch";

    protected OvsdbClient ovs = null;

    @Before
    public void setUp() throws InterruptedException, ExecutionException, TimeoutException, IOException {

        this.ovs = OpenVswitchSchemaSuiteIT.getOvsdbClient();

        if (this.ovs == null) {
            this.ovs = getTestConnection();
            OpenVswitchSchemaSuiteIT.setOvsdbClient(this.ovs);

            ListenableFuture<List<String>> databases = ovs.getDatabases();
            List<String> dbNames = databases.get();
            Assert.assertNotNull(dbNames);
            boolean hasOpenVswitchSchema = false;
            for (String dbName : dbNames) {
                if (dbName.equals(OPEN_VSWITCH_SCHEMA)) {
                    hasOpenVswitchSchema = true;
                    break;
                }
            }
            Assert.assertTrue(OPEN_VSWITCH_SCHEMA + " schema is not supported by the switch", hasOpenVswitchSchema);
        }
    }

    public Properties loadProperties() {
        Properties props = new Properties(System.getProperties());
        return props;
    }

    public OvsdbClient getTestConnection() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Properties props = loadProperties();
        String addressStr = props.getProperty(SERVER_IPADDRESS);
        String portStr = props.getProperty(SERVER_PORT, DEFAULT_SERVER_PORT);
        String connectionType = props.getProperty(CONNECTION_TYPE, "active");

        // If the connection type is active, controller connects to the ovsdb-server
        if (connectionType.equalsIgnoreCase(CONNECTION_TYPE_ACTIVE)) {
            if (addressStr == null) {
                Assert.fail(usage());
            }

            InetAddress address;
            try {
                address = InetAddress.getByName(addressStr);
            } catch (Exception e) {
                System.out.println("Unable to resolve " + addressStr);
                e.printStackTrace();
                return null;
            }

            Integer port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number : " + portStr);
                e.printStackTrace();
                return null;
            }

            OvsdbConnection connection = OvsdbConnectionService.getService();
            return connection.connect(address, port);
        } else if (connectionType.equalsIgnoreCase(CONNECTION_TYPE_PASSIVE)) {
            ExecutorService executor = Executors.newFixedThreadPool(1);
            Future<OvsdbClient> passiveConnection = executor.submit(new PassiveListener());
            return passiveConnection.get(60, TimeUnit.SECONDS);
        }
        Assert.fail("Connection parameter ("+CONNECTION_TYPE+") must be either active or passive");
        return null;
    }

    public UUID getOpenVSwitchTableUuid(OvsdbClient ovs, Map<String, Map<UUID, Row>> tableCache) {
        OpenVSwitch openVSwitch = ovs.getTypedRowWrapper(OpenVSwitch.class, null);
        Map<UUID, Row> ovsTable = tableCache.get(openVSwitch.getSchema().getName());
        if (ovsTable != null) {
            if (ovsTable.keySet().size() >= 1) {
                return (UUID)ovsTable.keySet().toArray()[0];
            }
        }
        return null;
    }

    private String usage() {
        return "Integration Test needs a valid connection configuration as follows :\n" +
               "active connection : mvn -Pintegrationtest -Dovsdbserver.ipaddress=x.x.x.x -Dovsdbserver.port=yyyy verify\n"+
               "passive connection : mvn -Pintegrationtest -Dovsdbserver.connection=passive verify\n";
    }

    public class PassiveListener implements Callable<OvsdbClient>, OvsdbConnectionListener {
        OvsdbClient client = null;
        @Override
        public OvsdbClient call() throws Exception {
            OvsdbConnection connection = OvsdbConnectionService.getService();
            connection.registerForPassiveConnection(this);
            while (client == null) {
                Thread.sleep(500);
            }
            return client;
        }

        @Override
        public void connected(OvsdbClient client) {
            this.client = client;
        }

        @Override
        public void disconnected(OvsdbClient client) {
            Assert.assertEquals(this.client.getConnectionInfo(), client.getConnectionInfo());
            this.client = null;
        }
    }
}
