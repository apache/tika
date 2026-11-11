# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""tika_java_module: the Bazel shape of one Maven module with the standard layout.

One call creates:
  :<name>   java_library from src/main/java and src/main/resources (the module jar)
  :tests    testonly java_library from src/test/java (the Maven test-jar)
  one java_junit5_test per class Surefire would run (Test*, *Test, *Tests, *TestCase)
  :all_tests  test_suite of those tests
"""

load("@contrib_rules_jvm//java:defs.bzl", "java_junit5_test")
load("@rules_java//java:defs.bzl", "java_library")

# Surefire's default includes.
SUREFIRE_INCLUDES = [
    "src/test/java/**/Test*.java",
    "src/test/java/**/*Test.java",
    "src/test/java/**/*Tests.java",
    "src/test/java/**/*TestCase.java",
]

# On every test compile classpath: tika-parent's junit dependencies, and tika-test-support,
# which tika-parent's "test-support" profile adds to every module that has src/test/java.
JUNIT_DEPS = [
    "@maven//:org_junit_jupiter_junit_jupiter_api",
    "@maven//:org_junit_jupiter_junit_jupiter_params",
    "//tika-test-support",
]

# What the contrib_rules_jvm JUnit 5 runner needs at runtime, plus the listener that the
# tika-parent "test-support" profile adds to every module with tests.
TEST_RUNTIME_DEPS = [
    "@maven//:org_junit_jupiter_junit_jupiter_engine",
    "@maven//:org_junit_platform_junit_platform_launcher",
    "@maven//:org_junit_platform_junit_platform_reporting",
    "//tika-test-support",
]

# Surefire's argLine in tika-parent. java.io.tmpdir is set to TEST_TMPDIR by the launcher.
TEST_JVM_FLAGS = [
    "-Xmx4g",
    "-Djava.awt.headless=true",
]

def _basedir_files_impl(ctx):
    pkg = ctx.label.package + "/"
    links = {f.short_path[len(pkg):]: f for f in ctx.files.srcs}
    return [DefaultInfo(runfiles = ctx.runfiles(symlinks = links))]

# Surefire runs tests in the module directory, so a test may open "src/test/resources/x"
# relative to it. Bazel runs tests at the runfiles root; this mirrors the package's files
# there under their package-relative paths.
basedir_files = rule(
    implementation = _basedir_files_impl,
    attrs = {"srcs": attr.label_list(allow_files = True)},
)

def _uniq(items):
    out = []
    for i in items:
        if i not in out:
            out.append(i)
    return out

def _test_name(path, taken):
    cls = path[len("src/test/java/"):-len(".java")].replace("/", ".")
    simple = cls.split(".")[-1]
    return cls, (cls if simple in taken else simple)

def tika_java_module(
        name,
        deps = [],
        runtime_deps = [],
        test_deps = [],
        test_runtime_deps = [],
        test_excludes = [],
        test_jvm_flags = [],
        test_data = [],
        test_cwd_is_basedir = False,
        resource_excludes = [],
        extra_srcs = [],
        plugins = [],
        javacopts = [],
        test_jar_resources = False,
        test_size = "medium",
        test_tags = [],
        visibility = ["//visibility:public"]):
    """Declares the library, test-jar and tests of one Maven module.

    Args:
      name: the Maven artifactId; also the library target name.
      deps: compile and provided scope dependencies.
      runtime_deps: runtime scope dependencies.
      test_deps: test scope dependencies (compile classpath of :tests).
      test_runtime_deps: extra runtime-only dependencies for the tests.
      test_excludes: test sources not to run (abstract bases, Surefire excludes).
      test_jvm_flags: extra JVM flags for every test in the module.
      test_data: extra runtime files for every test in the module.
      test_cwd_is_basedir: let tests open src/test/resources by module-relative path.
      resource_excludes: files under src/main/resources not to package.
      extra_srcs: generated sources (what a Maven generate-sources plugin adds).
      plugins: annotation processors javac would find on the Maven classpath.
      javacopts: extra javac options.
      test_jar_resources: package src/test/resources into :tests, as Maven's test-jar does.
        Only needed when another module depends on the test-jar.
      test_size: Bazel test size for the module's tests.
      test_tags: tags for the module's tests.
      visibility: visibility of the library and test-jar.
    """
    pkg = native.package_name()
    srcs = native.glob(["src/main/java/**/*.java"], allow_empty = True)
    resources = native.glob(
        ["src/main/resources/**"],
        exclude = resource_excludes,
        allow_empty = True,
    )
    java_library(
        name = name,
        srcs = srcs + extra_srcs,
        resources = resources,
        resource_strip_prefix = pkg + "/src/main/resources" if resources else None,
        javacopts = javacopts,
        plugins = plugins,
        deps = deps,
        runtime_deps = runtime_deps,
        visibility = visibility,
    )

    test_srcs = native.glob(["src/test/java/**/*.java"], allow_empty = True)
    if not test_srcs:
        return
    test_resources = native.glob(["src/test/resources/**"], allow_empty = True)
    # :tests is the module's test-jar for other modules. When it carries src/test/resources,
    # the module's own tests use the classes alone: they already read the resources as a
    # directory, and a second copy would make getResources() return everything twice.
    test_lib = "tests_classes" if test_jar_resources else "tests"
    java_library(
        name = test_lib,
        testonly = True,
        srcs = test_srcs,
        javacopts = javacopts,
        deps = _uniq([":" + name] + deps + test_deps + JUNIT_DEPS),
        visibility = visibility,
    )
    if test_jar_resources:
        java_library(
            name = "tests",
            testonly = True,
            resources = test_resources,
            resource_strip_prefix = pkg + "/src/test/resources" if test_resources else None,
            visibility = visibility,
            exports = [":" + test_lib],
        )

    # Tests read src/test/resources as a directory (Maven's target/test-classes): many call
    # new File(getResource(...).toURI()), which fails for a resource inside a jar. The
    # launcher puts the runfiles copy of that directory first on the classpath.
    args = []
    if test_resources:
        args = ["--main_advice_classpath=%s/src/test/resources" % pkg]

    if test_cwd_is_basedir:
        basedir_files(
            name = "basedir_test_resources",
            testonly = True,
            srcs = test_resources,
        )
        test_data = test_data + [":basedir_test_resources"]

    taken = {}
    for path in native.glob(SUREFIRE_INCLUDES, allow_empty = True):
        simple = path.split("/")[-1][:-len(".java")]
        taken[simple] = taken.get(simple, 0) + 1
    duplicated = [k for k, v in taken.items() if v > 1]

    tests = []
    for path in native.glob(SUREFIRE_INCLUDES, exclude = test_excludes + ["**/*$*"], allow_empty = True):
        cls, test_name = _test_name(path, duplicated)
        tests.append(":" + test_name)
        java_junit5_test(
            name = test_name,
            size = test_size,
            test_class = cls,
            args = args,
            data = test_resources + test_data,
            jvm_flags = TEST_JVM_FLAGS + test_jvm_flags,
            tags = test_tags,
            runtime_deps = _uniq([":" + test_lib] + TEST_RUNTIME_DEPS + test_runtime_deps),
        )
    native.test_suite(
        name = "all_tests",
        tests = tests,
    )
