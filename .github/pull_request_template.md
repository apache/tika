<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

Thanks for your contribution to [Apache Tika](https://tika.apache.org/)! Your help is appreciated!

Before opening the pull request, please verify that
* there is an open issue on the [Tika issue tracker](https://issues.apache.org/jira/projects/TIKA) which describes the problem or the improvement. We cannot accept pull requests without an issue because the change wouldn't be listed in the release notes.
* the issue ID (`TIKA-XXXX`)
  - is referenced in the title of the pull request
  - and placed in front of your commit messages surrounded by square brackets (`[TIKA-XXXX] Issue or pull request title`)
* commits are squashed into a single one (or few commits for larger changes)
* Tika is successfully built and unit tests pass by running `mvn clean test`
* there should be no conflicts when merging the pull request branch into the *recent* `main` branch. If there are conflicts, please try to rebase the pull request branch on top of a freshly pulled `main` branch
* if you add new module that downstream users will depend upon add it to relevant group in `tika-bom/pom.xml`.

We will be able to faster integrate your pull request if these conditions are met. If you have any questions how to fix your problem or about using Tika in general, please sign up for the [Tika mailing list](http://tika.apache.org/mail-lists.html). Thanks!
