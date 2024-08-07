/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.fetchers.microsoftgraph;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.microsoft.graph.drives.DrivesRequestBuilder;
import com.microsoft.graph.drives.item.DriveItemRequestBuilder;
import com.microsoft.graph.drives.item.items.ItemsRequestBuilder;
import com.microsoft.graph.drives.item.items.item.DriveItemItemRequestBuilder;
import com.microsoft.graph.drives.item.items.item.content.ContentRequestBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.ClientCertificateCredentialsConfig;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.MicrosoftGraphFetcherConfig;

@ExtendWith(MockitoExtension.class)
class MicrosoftGraphFetcherTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftGraphFetcherTest.class);
    static byte[] certificateBytes = "test cert file here".getBytes(StandardCharsets.UTF_8);
    static String certificatePassword = "somepasswordhere";
    static String clientId = "12312312-1234-1234-1234-112312312313";
    static String tenantId = "32132132-4332-5432-4321-121231231232";
    static String siteDriveId = "99999999-1234-1111-1111-12312312312";
    static String driveItemid = "asfsadfsadfsafdusahdfiuhfdsusadfjuafiagfaigf";

    @Mock
    GraphServiceClient graphClient;
    @Spy
    @SuppressWarnings("unused")
    MicrosoftGraphFetcherConfig microsoftGraphFetcherConfig = new MicrosoftGraphFetcherConfig().setCredentials(
            new ClientCertificateCredentialsConfig().setCertificateBytes(certificateBytes)
                    .setCertificatePassword(certificatePassword).setClientId(clientId)
                    .setTenantId(tenantId)).setScopes(Collections.singletonList(".default"));

    @Mock
    DrivesRequestBuilder drivesRequestBuilder;

    @Mock
    DriveItemRequestBuilder driveItemRequestBuilder;

    @Mock
    ItemsRequestBuilder itemsRequestBuilder;

    @Mock
    DriveItemItemRequestBuilder driveItemItemRequestBuilder;

    @Mock
    ContentRequestBuilder contentRequestBuilder;

    @InjectMocks
    MicrosoftGraphFetcher microsoftGraphFetcher;

    @Test
    void fetch() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            Mockito.when(graphClient.drives()).thenReturn(drivesRequestBuilder);
            Mockito.when(drivesRequestBuilder.byDriveId(siteDriveId))
                    .thenReturn(driveItemRequestBuilder);
            Mockito.when(driveItemRequestBuilder.items()).thenReturn(itemsRequestBuilder);
            Mockito.when(itemsRequestBuilder.byDriveItemId(driveItemid))
                    .thenReturn(driveItemItemRequestBuilder);
            Mockito.when(driveItemItemRequestBuilder.content()).thenReturn(contentRequestBuilder);
            String content = "content";
            Mockito.when(contentRequestBuilder.get())
                    .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            InputStream resultingInputStream =
                    microsoftGraphFetcher.fetch(siteDriveId + "," + driveItemid, new Metadata(), new ParseContext());
            Assertions.assertEquals(content,
                    IOUtils.toString(resultingInputStream, StandardCharsets.UTF_8));
        }
    }
}
