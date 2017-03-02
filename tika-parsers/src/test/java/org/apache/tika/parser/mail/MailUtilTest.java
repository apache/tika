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

package org.apache.tika.parser.mail;


import static org.junit.Assert.assertEquals;

import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

public class MailUtilTest {

    @Test
    public void testBasic() throws Exception {
        String s = "Julien Nioche (JIRA) <jira@apache.org>";
        assertExtracted("Julien Nioche (JIRA)", "jira@apache.org", s);

        s = "\"Julien Nioche (JIRA)\" <jira@apache.org>";
        assertExtracted("Julien Nioche (JIRA)", "jira@apache.org", s);

        s = "<jira@apache.org> Julien Nioche (JIRA) ";
        assertExtracted("Julien Nioche (JIRA)", "jira@apache.org", s);

        s = "<jira@apache.org> \"Julien Nioche (JIRA)\" ";
        assertExtracted("Julien Nioche (JIRA)", "jira@apache.org", s);

    }

    private void assertExtracted(String person, String email, String string) {
        Metadata m = new Metadata();
        MailUtil.setPersonAndEmail(string, Message.MESSAGE_FROM_NAME, Message.MESSAGE_FROM_EMAIL, m);
        assertEquals(person + " : " + m.get(Message.MESSAGE_FROM_NAME), person,
                m.get(Message.MESSAGE_FROM_NAME));
        assertEquals(email + " : " +
                m.get(Message.MESSAGE_FROM_EMAIL), email, m.get(Message.MESSAGE_FROM_EMAIL));
    }


}
