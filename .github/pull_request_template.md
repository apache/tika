Thanks for your contribution to [Apache Tika](https://tika.apache.org/)! Your help is appreciated!

Before opening the pull request, please verify that
* there is an open issue on the [Tika issue tracker](https://issues.apache.org/jira/projects/TIKA) which describes the problem or the improvement. We cannot accept pull requests without an issue because the change wouldn't be listed in the release notes.
* the issue ID (`TIKA-XXXX`)
  - is referenced in the title of the pull request
  - and placed in front of your commit messages surrounded by square brackets (`[TIKA-XXXX] Issue or pull request title`)
* commits are squashed into a single one (or few commits for larger changes)
* Tika is successfully built and unit tests pass by running `mvn clean test`
* there should be no conflicts when merging the pull request branch into the *recent* `main` branch. If there are conflicts, please try to rebase the pull request branch on top of a freshly pulled `main` branch.

We will be able to faster integrate your pull request if these conditions are met. If you have any questions how to fix your problem or about using Tika in general, please sign up for the [Tika mailing list](http://tika.apache.org/mail-lists.html). Thanks!
