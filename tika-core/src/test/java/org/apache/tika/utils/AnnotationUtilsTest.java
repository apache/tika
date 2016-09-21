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
package org.apache.tika.utils;

import aQute.bnd.annotation.metatype.Configurable;
import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * @since 6/1/16
 */
public class AnnotationUtilsTest {

    @Test
    public void testMisMatchType() {

        class MyParser extends Configurable {
            @Field(required = true) int config;
        }

        Map<String, Param> params = new HashMap<>();
        try {
            params.put("config", new Param<>("config", 1));

            MyParser bean = new MyParser();
            AnnotationUtils.assignFieldParams(bean, params);
            Assert.assertEquals(bean.config, 1);
        } catch (TikaConfigException e) {
            e.printStackTrace();
            Assert.fail("Exception Not expected");
        }

        params.clear();
        try {
            params.put("config", new Param<>("config", "a string value"));
            AnnotationUtils.assignFieldParams(new MyParser(), params);
            Assert.fail("Exception expected");
        } catch (TikaConfigException e) {
            //expected
        }
    }

    @Test
    public void testPrimitiveAndBoxedTypes() {

        class MyParser extends Configurable {
            @Field(required = true) int config;
            @Field(required = true, name = "config") Integer config2;
        }

        Map<String, Param> params = new HashMap<>();
        try {
            MyParser bean = new MyParser();
            int val = 100;
            params.put("config", new Param<>("config", val));
            AnnotationUtils.assignFieldParams(bean, params);
            Assert.assertTrue(bean.config == bean.config2);
            Assert.assertTrue(bean.config == val);
        } catch (TikaConfigException e) {
            e.printStackTrace();
            Assert.fail("Exception Not expected");
        }

    }

    @Test
    public void testRequiredParam() {

        class MyParser extends Configurable {
            @Field(required = true) String config;
        }

        Map<String, Param> params = new HashMap<>();
        String someval = "someval";
        params.put("config", new Param<>("config", someval));
        try {
            MyParser bean = new MyParser();
            AnnotationUtils.assignFieldParams(bean, params);
            Assert.assertEquals(bean.config, someval);
        } catch (TikaConfigException e) {
            e.printStackTrace();
            Assert.fail("Exception Not expected");
        }

        params.clear();
        try {
            AnnotationUtils.assignFieldParams(new MyParser(), params);
            Assert.fail("Exception expected");
        } catch (TikaConfigException e) {
            //expected
        }
    }


    @Test
    public void testParserInheritance() {

        class Parent {
            @Field(required = true) int overridden;
            @Field(required = true) int parentField;

        }

        class Child extends Parent {
            @Field(required = true) int overridden;
            @Field(required = true) int childField;
        }

        int val = 1;
        Map<String, Param> params = new HashMap<>();
        params.put("overridden", new Param<>("oevrriden", val));
        params.put("parentField", new Param<>("parentField", val));
        params.put("childField", new Param<>("childField", val));

        try {
            Child child = new Child();
            AnnotationUtils.assignFieldParams(child, params);
            Assert.assertEquals(child.overridden, val);
            Assert.assertEquals(child.parentField, val);
            Assert.assertEquals(child.childField, val);
        } catch (TikaConfigException e) {
            e.printStackTrace();
            Assert.fail("Exception Not expected");
        }

        try {
            params.remove("parentField");
            AnnotationUtils.assignFieldParams(new Child(), params);
            Assert.fail("Exception expected, parent class field not set");
        } catch (TikaConfigException e) {
            //expected
        }
    }



    @Test
    public void testParamValueInheritance() {

        class Bean {
            @Field(required = true) CharSequence field;
        }

        Bean parser = new Bean();
        Map<String, Param> params = new HashMap<>();
        try {
            String val = "someval";
            params.put("field", new Param<String>("field", String.class, val));
            AnnotationUtils.assignFieldParams(parser, params);
            Assert.assertEquals(val, parser.field);
        } catch (Exception e){
            e.printStackTrace();
            Assert.fail("Exception not expected, string is assignable to CharSequence");
        }

        try {
            Date val = new Date();
            params.put("field", new Param<Date>("field", Date.class, val));
            AnnotationUtils.assignFieldParams(parser, params);
            Assert.fail("Exception expected, Date is not assignable to CharSequence.");
        } catch (TikaConfigException e){
            //expected

        }

    }

}