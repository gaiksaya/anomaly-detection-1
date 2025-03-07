/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.ad.cluster;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Before;
import org.opensearch.ad.AbstractADTest;
import org.opensearch.ad.cluster.diskcleanup.ModelCheckpointIndexRetention;
import org.opensearch.ad.constant.CommonName;
import org.opensearch.ad.util.ClientUtil;
import org.opensearch.ad.util.DiscoveryNodeFilterer;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.component.LifecycleListener;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.threadpool.Scheduler.Cancellable;
import org.opensearch.threadpool.ThreadPool;

public class MasterEventListenerTests extends AbstractADTest {
    private ClusterService clusterService;
    private ThreadPool threadPool;
    private Client client;
    private Clock clock;
    private Cancellable hourlyCancellable;
    private Cancellable checkpointIndexRetentionCancellable;
    private MasterEventListener masterService;
    private ClientUtil clientUtil;
    private DiscoveryNodeFilterer nodeFilter;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        clusterService = mock(ClusterService.class);
        threadPool = mock(ThreadPool.class);
        hourlyCancellable = mock(Cancellable.class);
        checkpointIndexRetentionCancellable = mock(Cancellable.class);
        when(threadPool.scheduleWithFixedDelay(any(HourlyCron.class), any(TimeValue.class), any(String.class)))
            .thenReturn(hourlyCancellable);
        when(threadPool.scheduleWithFixedDelay(any(ModelCheckpointIndexRetention.class), any(TimeValue.class), any(String.class)))
            .thenReturn(checkpointIndexRetentionCancellable);
        client = mock(Client.class);
        clock = mock(Clock.class);
        clientUtil = mock(ClientUtil.class);
        HashMap<String, String> ignoredAttributes = new HashMap<String, String>();
        ignoredAttributes.put(CommonName.BOX_TYPE_KEY, CommonName.WARM_BOX_TYPE);
        nodeFilter = new DiscoveryNodeFilterer(clusterService);

        masterService = new MasterEventListener(clusterService, threadPool, client, clock, clientUtil, nodeFilter);
    }

    public void testOnOffMaster() {
        masterService.onMaster();
        assertThat(hourlyCancellable, is(notNullValue()));
        assertThat(checkpointIndexRetentionCancellable, is(notNullValue()));
        assertTrue(!masterService.getHourlyCron().isCancelled());
        assertTrue(!masterService.getCheckpointIndexRetentionCron().isCancelled());
        masterService.offMaster();
        assertThat(masterService.getCheckpointIndexRetentionCron(), is(nullValue()));
        assertThat(masterService.getHourlyCron(), is(nullValue()));
    }

    public void testBeforeStop() {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            assertTrue(String.format("The size of args is %d.  Its content is %s", args.length, Arrays.toString(args)), args.length == 1);

            LifecycleListener listener = null;
            if (args[0] instanceof LifecycleListener) {
                listener = (LifecycleListener) args[0];
            }

            assertTrue(listener != null);
            listener.beforeStop();

            return null;
        }).when(clusterService).addLifecycleListener(any());

        masterService.onMaster();
        assertThat(masterService.getCheckpointIndexRetentionCron(), is(nullValue()));
        assertThat(masterService.getHourlyCron(), is(nullValue()));
        masterService.offMaster();
        assertThat(masterService.getCheckpointIndexRetentionCron(), is(nullValue()));
        assertThat(masterService.getHourlyCron(), is(nullValue()));
    }
}
