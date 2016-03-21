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
package org.apache.tika.parser.exiftool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

public class ExifToolImageParserTest extends TikaTest {

    private static final Log logger = LogFactory.getLog(ExifToolImageParserTest.class);

    private final Parser parser = new ExiftoolImageParser();
    
    public static boolean canRun() {
        String exiftoolCmd = ExiftoolExecutableUtils.getExiftoolExecutable(null);
        String[] checkCmd = { exiftoolCmd, "-ver"};
        // If Exiftool is not on the path, do not run the test.
        return ExternalParser.check(checkCmd);
    }
    
    @Before
    public void setup()
    {
        assumeTrue(canRun());
    }

    @Test
    public void testJPEGIPTC() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_IPTC_EXT.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("Washington", metadata.get(IPTC.CITY));
        assertEquals("United States", metadata.get(IPTC.COUNTRY));
        assertEquals("US", metadata.get(IPTC.COUNTRY_CODE));
        
        assertEquals("A stream bank in Rock Creek Park Washington DC during a photo bike tour with ASPP DC/South chapter.", metadata.get(IPTC.DESCRIPTION));
        assertEquals("A stream bank in Rock Creek Park Washington DC during a photo bike tour with ASPP DC/South chapter.", metadata.get(Metadata.DESCRIPTION));
        
        assertEquals("Rock Creek Park", metadata.get(IPTC.HEADLINE));
        assertEquals("Downstream", metadata.get(Metadata.TITLE));
        
        assertEquals("intellectual genre", metadata.get(IPTC.INTELLECTUAL_GENRE));
        
        List<String> iptcKeywords = Arrays.asList(metadata.getValues(IPTC.KEYWORDS));
        assertTrue(iptcKeywords.contains("stream"));
        assertTrue(iptcKeywords.contains("park"));
        assertTrue(iptcKeywords.contains("bank"));
        assertEquals(5, iptcKeywords.size());
        List<String> tikaKeywords = Arrays.asList(metadata.getValues(Metadata.KEYWORDS));
        assertTrue(Arrays.toString(tikaKeywords.toArray()).contains("stream"));
        assertTrue(Arrays.toString(tikaKeywords.toArray()).contains("park"));
        assertTrue(Arrays.toString(tikaKeywords.toArray()).contains("bank"));
        assertEquals(5, tikaKeywords.size());
        
        
        assertEquals("DC", metadata.get(IPTC.PROVINCE_OR_STATE));
        
        List<String> iptcSceneCode = Arrays.asList(metadata.getValues(IPTC.SCENE_CODE));
        assertEquals(2, iptcSceneCode.size());
        assertTrue(Arrays.toString(iptcSceneCode.toArray()).contains("iptc scene 1"));
        assertTrue(Arrays.toString(iptcSceneCode.toArray()).contains("iptc scene 2"));
        
        List<String> iptcSubjectCode = Arrays.asList(metadata.getValues(IPTC.SUBJECT_CODE));
        assertEquals(2, iptcSubjectCode.size());
        assertTrue(Arrays.toString(iptcSubjectCode.toArray()).contains("iptc subject code 1"));
        assertTrue(Arrays.toString(iptcSubjectCode.toArray()).contains("iptc subject code 2"));
        
        assertEquals("Rock Creek Park", metadata.get(IPTC.SUBLOCATION));
        
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        calendar.set(Calendar.YEAR, 2011);
        calendar.set(Calendar.MONTH, 7);
        calendar.set(Calendar.DATE, 31);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        assertEquals(calendar.getTime(), metadata.getDate(IPTC.DATE_CREATED));
        
        assertEquals("Ray Gauss II", metadata.get(IPTC.DESCRIPTION_WRITER));
        assertEquals("instructions", metadata.get(IPTC.INSTRUCTIONS));
        assertEquals("job identifier", metadata.get(IPTC.JOB_ID));
        assertEquals("Downstream", metadata.get(IPTC.TITLE));
        assertTrue(metadata.get(IPTC.COPYRIGHT_NOTICE).contains("Ray Gauss II"));
        
        List<String> creators = Arrays.asList(metadata.getValues(IPTC.CREATOR));
        assertTrue(Arrays.toString(creators.toArray()).contains("Ray Gauss II"));
        
        assertEquals("DAM Architect", metadata.get(IPTC.CREATORS_JOB_TITLE));
        assertEquals("provider", metadata.get(IPTC.CREDIT_LINE));
        assertEquals("rights usage terms", metadata.get(IPTC.RIGHTS_USAGE_TERMS));
        assertEquals("source", metadata.get(IPTC.SOURCE));
        assertEquals("1234 Some Road", metadata.get(IPTC.CONTACT_INFO_ADDRESS));
        assertEquals("Atlanta", metadata.get(IPTC.CONTACT_INFO_CITY));
        assertEquals("US", metadata.get(IPTC.CONTACT_INFO_COUNTRY));
        
        List<String> ciWorkEmails = Arrays.asList(metadata.getValues(IPTC.CONTACT_INFO_EMAIL));
        // Photoshop does not support true multi-value here
        assertTrue(Arrays.toString(ciWorkEmails.toArray()).contains("info@alfresco.com"));
        assertTrue(Arrays.toString(ciWorkEmails.toArray()).contains("other@example.com"));
        
        List<String> ciWorkTels = Arrays.asList(metadata.getValues(IPTC.CONTACT_INFO_PHONE));
        // Photoshop does not support true multi-value here
        assertTrue(Arrays.toString(ciWorkTels.toArray()).contains("555-1234"));
        assertTrue(Arrays.toString(ciWorkTels.toArray()).contains("555-4321"));
        
        assertEquals("30339", metadata.get(IPTC.CONTACT_INFO_POSTAL_CODE));
        assertEquals("GA", metadata.get(IPTC.CONTACT_INFO_STATE_PROVINCE));
        
        List<String> ciWorkUrls = Arrays.asList(metadata.getValues(IPTC.CONTACT_INFO_WEB_URL));
        // Photoshop does not support true multi-value here
        assertTrue(Arrays.toString(ciWorkUrls.toArray()).contains("http://alfresco.com"));
        assertTrue(Arrays.toString(ciWorkUrls.toArray()).contains("http://example.com"));
        
        assertEquals("rocky 1 and rocky 2 are big", metadata.get(IPTC.ADDITIONAL_MODEL_INFO));
        
        List<String> orgCodes = Arrays.asList(metadata.getValues(IPTC.ORGANISATION_CODE));
        assertEquals(2, orgCodes.size());
        assertEquals("ASPP", orgCodes.get(0));
        assertEquals("OTHER_ORG", orgCodes.get(1));
        
        // List<String> cvTerms = Arrays.asList(metadata.getValues(IPTC.CONTROLLED_VOCABULARY_TERM));
        
        List<String> modelAges = Arrays.asList(metadata.getValues(IPTC.MODEL_AGE));
        assertEquals(2, modelAges.size());
        assertEquals("1000", modelAges.get(0));
        assertEquals("1001", modelAges.get(1));
        
        List<String> orgNames = Arrays.asList(metadata.getValues(IPTC.ORGANISATION_NAME));
        assertEquals(2, orgNames.size());
        assertEquals("ASPP", orgNames.get(0));
        assertEquals("Other Org", orgNames.get(1));
        
        List<String> peopleShown = Arrays.asList(metadata.getValues(IPTC.PERSON));
        assertEquals(2, peopleShown.size());
        assertEquals("rocky 1", peopleShown.get(0));
        assertEquals("rocky 2", peopleShown.get(1));
        
        assertEquals("http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture", metadata.get(IPTC.DIGITAL_SOURCE_TYPE));
        assertEquals("Photo Bike Tour", metadata.get(IPTC.EVENT));
        
        assertEquals("RGAUSS", metadata.get(IPTC.IMAGE_SUPPLIER_ID));
        assertEquals("Ray Gauss II", metadata.get(IPTC.IMAGE_SUPPLIER_NAME));
        assertEquals("supplier image ID", metadata.get(IPTC.IMAGE_SUPPLIER_IMAGE_ID));
        assertEquals("3456", metadata.get(IPTC.MAX_AVAIL_HEIGHT));
        assertEquals("5184", metadata.get(IPTC.MAX_AVAIL_WIDTH));
        assertEquals("1.2.0", metadata.get(IPTC.PLUS_VERSION));
        
        List<String> copyrightOwnerIds = Arrays.asList(metadata.getValues(IPTC.COPYRIGHT_OWNER_ID));
        assertEquals(1, copyrightOwnerIds.size());
        assertEquals("RGAUSS", copyrightOwnerIds.get(0));
        // assertEquals("", copyrightOwnerIds.get(1)); // TODO: Get ExifTool to preserve empty values
        
        List<String> copyrightOwnerNames = Arrays.asList(metadata.getValues(IPTC.COPYRIGHT_OWNER_NAME));
        assertEquals(2, copyrightOwnerNames.size());
        assertEquals("Ray Gauss II", copyrightOwnerNames.get(0));
        assertEquals("GG", copyrightOwnerNames.get(1));
        
        List<String> imageCreatorIds = Arrays.asList(metadata.getValues(IPTC.IMAGE_CREATOR_ID));
        assertEquals(1, imageCreatorIds.size());
        assertEquals("RGAUSS", imageCreatorIds.get(0));
        // assertEquals("", imageCreatorIds.get(1)); // TODO: Get ExifTool to preserve empty values
        
        assertTrue(metadata.isMultiValued(IPTC.IMAGE_CREATOR_NAME));
        List<String> imageCreatorNames = Arrays.asList(metadata.getValues(IPTC.IMAGE_CREATOR_NAME));
        assertEquals(2, imageCreatorNames.size());
        assertEquals("Ray Gauss II", imageCreatorNames.get(0));
        assertEquals("GG", imageCreatorNames.get(1));
        
        List<String> licensorIds = Arrays.asList(metadata.getValues(IPTC.LICENSOR_ID));
        assertEquals("RGAUSS", licensorIds.get(0));
        
        assertTrue(metadata.isMultiValued(IPTC.LICENSOR_NAME));
        List<String> licensorNames = Arrays.asList(metadata.getValues(IPTC.LICENSOR_NAME));
        assertEquals(2, licensorNames.size());
        assertEquals("Ray Gauss II", licensorNames.get(0));
        assertEquals("GG", licensorNames.get(1));
        
        // Photoshop does not support licensor addresses, cities, or countries
        
        List<String> licensorEmails = Arrays.asList(metadata.getValues(IPTC.LICENSOR_EMAIL));
        assertEquals("r@example.com", licensorEmails.get(0));
        // assertEquals("", licensorEmails.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> licensorTel1 = Arrays.asList(metadata.getValues(IPTC.LICENSOR_TELEPHONE_1));
        assertEquals("555-5555", licensorTel1.get(0));
        // assertEquals("", licensorTel1.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> licensorTel2 = Arrays.asList(metadata.getValues(IPTC.LICENSOR_TELEPHONE_2));
        assertEquals("555-4444", licensorTel2.get(0));
        // assertEquals("", licensorTel2.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> licensorUrls = Arrays.asList(metadata.getValues(IPTC.LICENSOR_URL));
        assertEquals("http://rgauss.com", licensorUrls.get(0));
        // assertEquals("", licensorUrls.get(1)); // TODO: Get ExifTool to preserve empty values
        
        assertEquals("Age Unknown", metadata.get(IPTC.MINOR_MODEL_AGE_DISCLOSURE));
        List<String> modelReleaseIds = Arrays.asList(metadata.getValues(IPTC.MODEL_RELEASE_ID));
        assertEquals("model release id 1", modelReleaseIds.get(0));
        assertEquals("model release id 2", modelReleaseIds.get(1));
        assertEquals("Not Applicable", metadata.get(IPTC.MODEL_RELEASE_STATUS));
        
        List<String> propertyReleaseIds = Arrays.asList(metadata.getValues(IPTC.PROPERTY_RELEASE_ID));
        assertEquals("prop release id 1", propertyReleaseIds.get(0));
        assertEquals("prop release id 2", propertyReleaseIds.get(1));
        assertEquals("Not Applicable", metadata.get(IPTC.PROPERTY_RELEASE_STATUS));
        
        List<String> aoCopyright = Arrays.asList(metadata.getValues(IPTC.ARTWORK_OR_OBJECT_DETAIL_COPYRIGHT_NOTICE));
        assertEquals("Ray Gauss II", aoCopyright.get(0));
        // assertEquals("", aoCopyright.get(1)); // TODO: Get ExifTool to preserve empty values
        // assertEquals("", aoCopyright.get(2)); // TODO: Get ExifTool to preserve empty values
        List<String> aoCreator = Arrays.asList(metadata.getValues(IPTC.ARTWORK_OR_OBJECT_DETAIL_CREATOR));
        assertEquals("Mother Nature", aoCreator.get(0));
        assertEquals("Man", aoCreator.get(1));
        assertEquals("Mother Nature", aoCreator.get(2));
        List<String> aoDateCreated = Arrays.asList(metadata.getValues(IPTC.ARTWORK_OR_OBJECT_DETAIL_DATE_CREATED));
        assertEquals("1890:01:01", aoDateCreated.get(0));
        // assertEquals("", aoDateCreated.get(1)); // TODO: Get ExifTool to preserve empty values
        assertEquals("1901:02:01", aoDateCreated.get(1));
        // assertEquals("", aoDateCreated.get(2)); // TODO: Get ExifTool to preserve empty values
        List<String> aoSource = Arrays.asList(metadata.getValues(IPTC.ARTWORK_OR_OBJECT_DETAIL_SOURCE));
        assertEquals("National Park Service", aoSource.get(0));
        // assertEquals("", aoSource.get(1)); // TODO: Get ExifTool to preserve empty values
        // assertEquals("", aoSource.get(2)); // TODO: Get ExifTool to preserve empty values
        List<String> aoSourceInventoryNum = Arrays.asList(metadata.getValues(IPTC.ARTWORK_OR_OBJECT_DETAIL_SOURCE_INVENTORY_NUMBER));
        assertEquals("123456", aoSourceInventoryNum.get(0));
        // assertEquals("", aoSourceInventoryNum.get(1)); // TODO: Get ExifTool to preserve empty values
        assertEquals("654321", aoSourceInventoryNum.get(1)); // This should be index 2, TODO: Get ExifTool to preserve empty values
        List<String> aoSourceTitles = Arrays.asList(metadata.getValues(IPTC.ARTWORK_OR_OBJECT_DETAIL_TITLE));
        assertEquals("Rock Creek Stream Bank", aoSourceTitles.get(0));
        assertEquals("Pollution", aoSourceTitles.get(1));
        assertEquals("Some Tree", aoSourceTitles.get(2));
        
        List<String> locationShownCity = Arrays.asList(metadata.getValues(IPTC.LOCATION_SHOWN_CITY));
        assertEquals("Washington", locationShownCity.get(0));
        // assertEquals("", locationShownCity.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> locationShownCountryCode = Arrays.asList(metadata.getValues(IPTC.LOCATION_SHOWN_COUNTRY_CODE));
        assertEquals("US", locationShownCountryCode.get(0));
        // assertEquals("", locationShownCountryCode.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> locationShownCountryName = Arrays.asList(metadata.getValues(IPTC.LOCATION_SHOWN_COUNTRY_NAME));
        assertEquals("United States", locationShownCountryName.get(0));
        // assertEquals("", locationShownCountryName.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> locationShownState = Arrays.asList(metadata.getValues(IPTC.LOCATION_SHOWN_PROVINCE_OR_STATE));
        assertEquals("D.C.", locationShownState.get(0));
        // assertEquals("", locationShownState.get(1)); // TODO: Get ExifTool to preserve empty values
        List<String> locationShownSublocation = Arrays.asList(metadata.getValues(IPTC.LOCATION_SHOWN_SUBLOCATION));
        assertEquals("Rock Creek Park Sub", locationShownSublocation.get(0));
        assertEquals("Stream Section", locationShownSublocation.get(1));
        List<String> locationShownWorldRegion = Arrays.asList(metadata.getValues(IPTC.LOCATION_SHOWN_WORLD_REGION));
        assertEquals("North America", locationShownWorldRegion.get(0));
        // assertEquals("", locationShownWorldRegion.get(1)); // TODO: Get ExifTool to preserve empty values
        
        assertEquals("Washington", metadata.get(IPTC.LOCATION_CREATED_CITY));
        assertEquals("US", metadata.get(IPTC.LOCATION_CREATED_COUNTRY_CODE));
        assertEquals("United States", metadata.get(IPTC.LOCATION_CREATED_COUNTRY_NAME));
        assertEquals("D.C.", metadata.get(IPTC.LOCATION_CREATED_PROVINCE_OR_STATE));
        assertEquals("Rock Creek Park", metadata.get(IPTC.LOCATION_CREATED_SUBLOCATION));
        assertEquals("North America", metadata.get(IPTC.LOCATION_CREATED_WORLD_REGION));

        assertTrue(IPTC.REGISTRY_ENTRY_CREATED_ORGANISATION_ID.isMultiValuePermitted());
        assertTrue(metadata.isMultiValued(IPTC.REGISTRY_ENTRY_CREATED_ORGANISATION_ID));
        List<String> registryEntryOrgIds = Arrays.asList(metadata.getValues(IPTC.REGISTRY_ENTRY_CREATED_ORGANISATION_ID));
        assertEquals(2, registryEntryOrgIds.size());
        assertEquals("PLUS", registryEntryOrgIds.get(0));
        // assertEquals("", registryEntryOrgIds.get(1)); // TODO: Get ExifTool to preserve empty values
        assertEquals("ORG 2", registryEntryOrgIds.get(1)); // This should be index 2, TODO: Get ExifTool to preserve empty values

        assertTrue(IPTC.REGISTRY_ENTRY_CREATED_ORGANISATION_ID.isMultiValuePermitted());
        assertTrue(metadata.isMultiValued(IPTC.REGISTRY_ENTRY_CREATED_ITEM_ID));
        List<String> registryEntryItemIds = Arrays.asList(metadata.getValues(IPTC.REGISTRY_ENTRY_CREATED_ITEM_ID));
        assertEquals(registryEntryItemIds.size(), 3);
        assertEquals("100-ABC-ABC-555", registryEntryItemIds.get(0));
        assertEquals("11223344", registryEntryItemIds.get(1));
        assertEquals("55667788", registryEntryItemIds.get(2));

    }

    @Test
    public void testJPEGCustomXmp() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_IPTC_EXT.jpg");
        ArrayList<Property> passthroughXmpProperties = new ArrayList<Property>(2);
        passthroughXmpProperties.add(Property.internalText("XMP-custom:Text"));
        passthroughXmpProperties.add(Property.internalText("XMP-custom:TextML"));
        Parser passthroughParser = new ExiftoolImageParser(null, passthroughXmpProperties);
        passthroughParser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("customTextField", metadata.get("XMP-custom:Text"));
        assertEquals("customMultilineField", metadata.get("XMP-custom:TextML"));
     }

    @Test
    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("100", metadata.get(TIFF.IMAGE_WIDTH));
        for (String name : metadata.names()) {
            logger.trace("JPEG-- " + name + "=" + metadata.get(name));
        }
    }

    @Test
    public void testPNGIPTC() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testPNG_IPTC.png");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        for (String name : metadata.names()) {
            logger.trace("PNG-- " + name + "=" + metadata.get(name));
        }
        assertEquals("100", metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals("Cat in a garden", metadata.get(IPTC.HEADLINE));
    }

    @Test
    public void testTIFFIPTC() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testTIFF_IPTC.tif");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        for (String name : metadata.names()) {
            logger.trace("TIFF-- " + name + "=" + metadata.get(name));
        }
        List<String> iptcKeywords = Arrays.asList(metadata.getValues(IPTC.KEYWORDS));
        assertTrue(iptcKeywords.contains("garden"));
        assertTrue(iptcKeywords.contains("cat"));
        assertEquals("Cat in a garden", metadata.get(IPTC.HEADLINE));
        assertEquals("100", metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals("75", metadata.get(TIFF.IMAGE_LENGTH));
        assertEquals("Inch", metadata.get(TIFF.RESOLUTION_UNIT));
        assertEquals("1", metadata.get(TIFF.ORIENTATION));
        assertEquals("8", metadata.get(TIFF.BITS_PER_SAMPLE));        
    }

}
