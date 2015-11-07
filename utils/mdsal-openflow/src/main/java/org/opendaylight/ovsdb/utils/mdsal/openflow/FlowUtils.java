/*
 * Copyright (c) 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ovsdb.utils.mdsal.openflow;

import com.google.common.base.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FlowUtils.class);
    private static final String OPENFLOW = "openflow";

    public static String getNodeName(long dpidLong) {
        return OPENFLOW + ":" + dpidLong;
    }

    public static NodeConnectorId getNodeConnectorId(long ofPort, String nodeName) {
        return new NodeConnectorId(nodeName + ":" + ofPort);
    }

    public static NodeConnectorId getNodeConnectorId(long dpidLong, long ofPort) {
        return getNodeConnectorId(ofPort, getNodeName(dpidLong));
    }

    public static NodeBuilder createNodeBuilder(String nodeId) {
        NodeBuilder builder = new NodeBuilder();
        builder.setId(new NodeId(nodeId));
        builder.setKey(new NodeKey(builder.getId()));
        return builder;
    }

    public static NodeBuilder createNodeBuilder(long dpidLong) {
        return createNodeBuilder(getNodeName(dpidLong));
    }

    public static InstanceIdentifier<Flow> createFlowPath(FlowBuilder flowBuilder, NodeBuilder nodeBuilder) {
        return InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeBuilder.getKey())
                .augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flowBuilder.getTableId()))
                .child(Flow.class, flowBuilder.getKey()).build();
    }

    public static InstanceIdentifier<Node> createNodePath(NodeBuilder nodeBuilder) {
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, nodeBuilder.getKey()).build();
    }

    public static Flow getFlow(FlowBuilder flowBuilder, NodeBuilder nodeBuilder,
                               ReadOnlyTransaction readTx, final LogicalDatastoreType store) {
        try {
            Optional<Flow> data = readTx.read(store, createFlowPath(flowBuilder, nodeBuilder)).get();
            if (data.isPresent()) {
                return data.get();
            }
        } catch (InterruptedException|ExecutionException e) {
            LOG.error(e.getMessage(), e);
        }

        LOG.info("Cannot find data for Flow {}", flowBuilder.getFlowName());
        return null;
    }

    public static FlowBuilder getPipelineFlow(short table, short gotoTable) {
        FlowBuilder flowBuilder = new FlowBuilder();
        flowBuilder.setMatch(new MatchBuilder().build());

        String flowId = "DEFAULT_PIPELINE_FLOW_" + table;
        flowBuilder.setId(new FlowId(flowId));
        FlowKey key = new FlowKey(new FlowId(flowId));
        flowBuilder.setStrict(true);
        flowBuilder.setBarrier(false);
        flowBuilder.setTableId(table);
        flowBuilder.setKey(key);
        flowBuilder.setFlowName(flowId);
        flowBuilder.setHardTimeout(0);
        flowBuilder.setIdleTimeout(0);
        flowBuilder.setPriority(0);
        return flowBuilder;
    }
}
