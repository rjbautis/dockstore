/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.client.cli.nested;

import java.util.Optional;

import io.dockstore.common.LanguageType;
import io.github.collaboratory.cwl.CWLClient;
import io.github.collaboratory.nextflow.NextFlowClient;
import io.github.collaboratory.wdl.WDLClient;

public final class LanguageClientFactory {

    private LanguageClientFactory() {
        // suppress constructor
    }

    public static Optional<LanguageClientInterface> createLanguageCLient(AbstractEntryClient client, LanguageType type) {
        if (type == LanguageType.CWL) {
            return Optional.of(new CWLClient(client));
        } else if (type == LanguageType.WDL) {
            return Optional.of(new WDLClient(client));
        } else if (type == LanguageType.NEXTFLOW) {
            return Optional.of(new NextFlowClient(client));
        } else if (type == LanguageType.NONE) {
            return Optional.empty();
        } else {
            throw new UnsupportedOperationException("language client does not exist for " + type);
        }
    }
}
