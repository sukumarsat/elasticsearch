/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.search.Query;
import org.elasticsearch.Version;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.MapperService.MergeReason;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.elasticsearch.test.VersionUtils.randomVersionBetween;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class RootObjectMapperTests extends MapperServiceTestCase {

    public void testNumericDetection() throws Exception {
        MergeReason reason = randomFrom(MergeReason.MAPPING_UPDATE, MergeReason.INDEX_TEMPLATE);
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(MapperService.SINGLE_MAPPING_NAME)
                        .field("numeric_detection", false)
                    .endObject()
                .endObject());
        MapperService mapperService = createMapperService(mapping);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());

        // update with a different explicit value
        String mapping2 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .field("numeric_detection", true)
                .endObject()
            .endObject());
        merge(mapperService, reason, mapping2);
        assertEquals(mapping2, mapperService.documentMapper().mappingSource().toString());

        // update with an implicit value: no change
        String mapping3 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                .endObject()
            .endObject());
        merge(mapperService, reason, mapping3);
        assertEquals(mapping2, mapperService.documentMapper().mappingSource().toString());
    }

    public void testDateDetection() throws Exception {
        MergeReason reason = randomFrom(MergeReason.MAPPING_UPDATE, MergeReason.INDEX_TEMPLATE);
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(MapperService.SINGLE_MAPPING_NAME)
                        .field("date_detection", true)
                    .endObject()
                .endObject());
        MapperService mapperService = createMapperService(mapping);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());

        // update with a different explicit value
        String mapping2 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .field("date_detection", false)
                .endObject()
            .endObject());
        merge(mapperService, reason, mapping2);
        assertEquals(mapping2, mapperService.documentMapper().mappingSource().toString());

        // update with an implicit value: no change
        String mapping3 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                .endObject()
            .endObject());
        merge(mapperService, reason, mapping3);
        assertEquals(mapping2, mapperService.documentMapper().mappingSource().toString());
    }

    public void testDateFormatters() throws Exception {
        MergeReason reason = randomFrom(MergeReason.MAPPING_UPDATE, MergeReason.INDEX_TEMPLATE);
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(MapperService.SINGLE_MAPPING_NAME)
                        .field("dynamic_date_formats", Collections.singletonList("yyyy-MM-dd"))
                    .endObject()
                .endObject());
        MapperService mapperService = createMapperService(mapping);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());

        // no update if formatters are not set explicitly
        String mapping2 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                .endObject()
            .endObject());
        merge(mapperService, reason, mapping2);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());

        String mapping3 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .field("dynamic_date_formats", Collections.emptyList())
                .endObject()
            .endObject());
        merge(mapperService, reason, mapping3);
        assertEquals(mapping3, mapperService.documentMapper().mappingSource().toString());
    }

    public void testDynamicTemplates() throws Exception {
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                    .startObject(MapperService.SINGLE_MAPPING_NAME)
                        .startArray("dynamic_templates")
                            .startObject()
                                .startObject("my_template")
                                    .field("match_mapping_type", "string")
                                    .startObject("mapping")
                                        .field("type", "keyword")
                                    .endObject()
                                .endObject()
                            .endObject()
                        .endArray()
                    .endObject()
                .endObject());
        MapperService mapperService = createMapperService(mapping);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());

        // no update if templates are not set explicitly
        String mapping2 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                .endObject()
            .endObject());
        merge(mapperService, mapping2);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());

        String mapping3 = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .field("dynamic_templates", Collections.emptyList())
                .endObject()
            .endObject());
        merge(mapperService, mapping3);
        assertEquals(mapping3, mapperService.documentMapper().mappingSource().toString());
    }

    public void testDynamicTemplatesForIndexTemplate() throws IOException {
        String mapping = Strings.toString(XContentFactory.jsonBuilder().startObject()
            .startArray("dynamic_templates")
                .startObject()
                    .startObject("first_template")
                        .field("path_match", "first")
                        .startObject("mapping")
                            .field("type", "keyword")
                        .endObject()
                    .endObject()
                .endObject()
                .startObject()
                    .startObject("second_template")
                        .field("path_match", "second")
                        .startObject("mapping")
                            .field("type", "keyword")
                        .endObject()
                    .endObject()
                .endObject()
            .endArray()
        .endObject());
        MapperService mapperService = createMapperService(Version.CURRENT, Settings.EMPTY, () -> true);
        mapperService.merge(MapperService.SINGLE_MAPPING_NAME, new CompressedXContent(mapping), MergeReason.INDEX_TEMPLATE);

        // There should be no update if templates are not set.
        mapping = Strings.toString(XContentFactory.jsonBuilder().startObject()
            .startObject("properties")
                .startObject("field")
                    .field("type", "integer")
                .endObject()
            .endObject()
        .endObject());
        DocumentMapper mapper = mapperService.merge(MapperService.SINGLE_MAPPING_NAME,
            new CompressedXContent(mapping), MergeReason.INDEX_TEMPLATE);

        DynamicTemplate[] templates = mapper.root().dynamicTemplates();
        assertEquals(2, templates.length);
        assertEquals("first_template", templates[0].name());
        assertEquals("first", templates[0].pathMatch());
        assertEquals("second_template", templates[1].name());
        assertEquals("second", templates[1].pathMatch());

        // Dynamic templates should be appended and deduplicated.
        mapping = Strings.toString(XContentFactory.jsonBuilder().startObject()
            .startArray("dynamic_templates")
                .startObject()
                    .startObject("third_template")
                        .field("path_match", "third")
                        .startObject("mapping")
                            .field("type", "integer")
                        .endObject()
                    .endObject()
                .endObject()
                .startObject()
                    .startObject("second_template")
                        .field("path_match", "second_updated")
                        .startObject("mapping")
                            .field("type", "double")
                        .endObject()
                    .endObject()
                .endObject()
            .endArray()
        .endObject());
        mapper = mapperService.merge(MapperService.SINGLE_MAPPING_NAME, new CompressedXContent(mapping), MergeReason.INDEX_TEMPLATE);

        templates = mapper.root().dynamicTemplates();
        assertEquals(3, templates.length);
        assertEquals("first_template", templates[0].name());
        assertEquals("first", templates[0].pathMatch());
        assertEquals("second_template", templates[1].name());
        assertEquals("second_updated", templates[1].pathMatch());
        assertEquals("third_template", templates[2].name());
        assertEquals("third", templates[2].pathMatch());
    }

    public void testIllegalFormatField() throws Exception {
        String dynamicMapping = Strings.toString(XContentFactory.jsonBuilder()
            .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .startArray("dynamic_date_formats")
                        .startArray().value("test_format").endArray()
                    .endArray()
                .endObject()
            .endObject());
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
            .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .startArray("date_formats")
                        .startArray().value("test_format").endArray()
                    .endArray()
                .endObject()
            .endObject());
        for (String m : Arrays.asList(mapping, dynamicMapping)) {
            MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(m));
            assertEquals("Failed to parse mapping: Invalid format: [[test_format]]: expected string value", e.getMessage());
        }
    }

    public void testIllegalDynamicTemplates() throws Exception {
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                .startObject(MapperService.SINGLE_MAPPING_NAME)
                    .startObject("dynamic_templates")
                    .endObject()
                .endObject()
            .endObject());

        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: Dynamic template syntax error. An array of named objects is expected.", e.getMessage());
    }

    public void testIllegalDynamicTemplateUnknownFieldType() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder();
        mapping.startObject();
        {
            mapping.startObject(MapperService.SINGLE_MAPPING_NAME);
            mapping.startArray("dynamic_templates");
            {
                mapping.startObject();
                mapping.startObject("my_template");
                mapping.field("match_mapping_type", "string");
                mapping.startObject("mapping");
                mapping.field("type", "string");
                mapping.endObject();
                mapping.endObject();
                mapping.endObject();
            }
            mapping.endArray();
            mapping.endObject();
        }
        mapping.endObject();
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertThat(e.getRootCause(), instanceOf(IllegalArgumentException.class));
        assertThat(e.getRootCause().getMessage(), equalTo("No mapper found for type [string]"));
    }

    public void testIllegalDynamicTemplateUnknownAttribute() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder();
        mapping.startObject();
        {
            mapping.startObject(MapperService.SINGLE_MAPPING_NAME);
            mapping.startArray("dynamic_templates");
            {
                mapping.startObject();
                mapping.startObject("my_template");
                mapping.field("match_mapping_type", "string");
                mapping.startObject("mapping");
                mapping.field("type", "keyword");
                mapping.field("foo", "bar");
                mapping.endObject();
                mapping.endObject();
                mapping.endObject();
            }
            mapping.endArray();
            mapping.endObject();
        }
        mapping.endObject();
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertThat(e.getRootCause(), instanceOf(MapperParsingException.class));
        assertThat(e.getRootCause().getMessage(),
            equalTo("unknown parameter [foo] on mapper [__dynamic__my_template] of type [keyword]"));
    }

    public void testIllegalDynamicTemplateInvalidAttribute() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder();
        mapping.startObject();
        {
            mapping.startObject(MapperService.SINGLE_MAPPING_NAME);
            mapping.startArray("dynamic_templates");
            {
                mapping.startObject();
                mapping.startObject("my_template");
                mapping.field("match_mapping_type", "string");
                mapping.startObject("mapping");
                mapping.field("type", "text");
                mapping.field("analyzer", "foobar");
                mapping.endObject();
                mapping.endObject();
                mapping.endObject();
            }
            mapping.endArray();
            mapping.endObject();
        }
        mapping.endObject();
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertThat(e.getRootCause(), instanceOf(IllegalArgumentException.class));
        assertThat(e.getRootCause().getMessage(), equalTo("analyzer [foobar] has not been configured in mappings"));
    }

    public void testIllegalDynamicTemplateNoMappingType() throws Exception {
        MapperService mapperService;
        {
            XContentBuilder mapping = XContentFactory.jsonBuilder();
            mapping.startObject();
            {
                mapping.startObject(MapperService.SINGLE_MAPPING_NAME);
                mapping.startArray("dynamic_templates");
                {
                    mapping.startObject();
                    mapping.startObject("my_template");
                    if (randomBoolean()) {
                        mapping.field("match_mapping_type", "*");
                    } else {
                        mapping.field("match", "string_*");
                    }
                    mapping.startObject("mapping");
                    mapping.field("type", "{dynamic_type}");
                    mapping.field("index_phrases", true);
                    mapping.endObject();
                    mapping.endObject();
                    mapping.endObject();
                }
                mapping.endArray();
                mapping.endObject();
            }
            mapping.endObject();
            mapperService = createMapperService(mapping);
            assertThat(mapperService.documentMapper().mappingSource().toString(), containsString("\"index_phrases\":true"));
        }
        {
            XContentBuilder mapping = XContentFactory.jsonBuilder();
            mapping.startObject();
            {
                mapping.startObject(MapperService.SINGLE_MAPPING_NAME);
                mapping.startArray("dynamic_templates");
                {
                    mapping.startObject();
                    mapping.startObject("my_template");
                    if (randomBoolean()) {
                        mapping.field("match_mapping_type", "*");
                    } else {
                        mapping.field("match", "string_*");
                    }
                    mapping.startObject("mapping");
                    mapping.field("type", "{dynamic_type}");
                    mapping.field("foo", "bar");
                    mapping.endObject();
                    mapping.endObject();
                    mapping.endObject();
                }
                mapping.endArray();
                mapping.endObject();
            }
            mapping.endObject();
            MapperParsingException e = expectThrows(MapperParsingException.class, () -> merge(mapperService, mapping));
            assertThat(e.getRootCause(), instanceOf(MapperParsingException.class));
            assertThat(e.getRootCause().getMessage(),
                equalTo("unknown parameter [foo] on mapper [__dynamic__my_template] of type [binary]"));
        }
    }

    public void testIllegalDynamicTemplate7DotXIndex() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder();
        mapping.startObject();
        {
            mapping.startObject(MapperService.SINGLE_MAPPING_NAME);
            mapping.startArray("dynamic_templates");
            {
                mapping.startObject();
                mapping.startObject("my_template");
                mapping.field("match_mapping_type", "string");
                mapping.startObject("mapping");
                mapping.field("type", "string");
                mapping.endObject();
                mapping.endObject();
                mapping.endObject();
            }
            mapping.endArray();
            mapping.endObject();
        }
        mapping.endObject();
        Version createdVersion = randomVersionBetween(random(), Version.V_7_0_0, Version.V_7_7_0);
        MapperService mapperService = createMapperService(createdVersion, mapping);
        assertThat(mapperService.documentMapper().mappingSource().toString(), containsString("\"type\":\"string\""));
        assertWarnings("dynamic template [my_template] has invalid content [{\"match_mapping_type\":\"string\",\"mapping\":{\"type\":" +
            "\"string\"}}], attempted to validate it with the following match_mapping_type: [[string]], " +
            "last error: [No mapper found for type [string]]");
    }

    @Override
    protected Collection<? extends Plugin> getPlugins() {
        return Collections.singletonList(new RuntimeFieldPlugin());
    }

    public void testRuntimeSection() throws IOException {
        String mapping = Strings.toString(runtimeMapping(builder -> {
            builder.startObject("field1").field("type", "test").field("prop1", "value1").endObject();
            builder.startObject("field2").field("type", "test").field("prop2", "value2").endObject();
            builder.startObject("field3").field("type", "test").endObject();
        }));
        MapperService mapperService = createMapperService(mapping);
        assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());
    }

    public void testRuntimeSectionMerge() throws IOException {
        MapperService mapperService;
        {
            String mapping = Strings.toString(fieldMapping(b -> b.field("type", "keyword")));
            mapperService = createMapperService(mapping);
            assertEquals(mapping, mapperService.documentMapper().mappingSource().toString());
            MappedFieldType field = mapperService.fieldType("field");
            assertThat(field, instanceOf(KeywordFieldMapper.KeywordFieldType.class));
        }
        {
            String mapping = Strings.toString(runtimeMapping(builder -> {
                builder.startObject("field").field("type", "test").field("prop1", "first version").endObject();
                builder.startObject("field2").field("type", "test").endObject();
            }));
            merge(mapperService, mapping);
            //field overrides now the concrete field already defined
            RuntimeField field = (RuntimeField)mapperService.fieldType("field");
            assertEquals("first version", field.prop1);
            assertNull(field.prop2);
            RuntimeField field2 = (RuntimeField)mapperService.fieldType("field2");
            assertNull(field2.prop1);
            assertNull(field2.prop2);
        }
        {
            String mapping = Strings.toString(runtimeMapping(
                //the existing runtime field gets updated
                builder -> builder.startObject("field").field("type", "test").field("prop2", "second version").endObject()));
            merge(mapperService, mapping);
            RuntimeField field = (RuntimeField)mapperService.fieldType("field");
            assertNull(field.prop1);
            assertEquals("second version", field.prop2);
            RuntimeField field2 = (RuntimeField)mapperService.fieldType("field2");
            assertNull(field2.prop1);
            assertNull(field2.prop2);
        }
        {
            String mapping = Strings.toString(mapping(builder -> builder.startObject("concrete").field("type", "keyword").endObject()));
            merge(mapperService, mapping);
            RuntimeField field = (RuntimeField)mapperService.fieldType("field");
            assertNull(field.prop1);
            assertEquals("second version", field.prop2);
            RuntimeField field2 = (RuntimeField)mapperService.fieldType("field2");
            assertNull(field2.prop1);
            assertNull(field2.prop2);
            MappedFieldType concrete = mapperService.fieldType("concrete");
            assertThat(concrete, instanceOf(KeywordFieldMapper.KeywordFieldType.class));
        }
        {
            String mapping = Strings.toString(runtimeMapping(
                builder -> builder.startObject("field3").field("type", "test").field("prop1", "value").endObject()));
            merge(mapperService, mapping);
            assertEquals("{\"_doc\":" +
                    "{\"runtime\":{" +
                    "\"field\":{\"type\":\"test\",\"prop2\":\"second version\"}," +
                    "\"field2\":{\"type\":\"test\"}," +
                    "\"field3\":{\"type\":\"test\",\"prop1\":\"value\"}}," +
                    "\"properties\":{" +
                    "\"concrete\":{\"type\":\"keyword\"}," +
                    "\"field\":{\"type\":\"keyword\"}}}}",
                mapperService.documentMapper().mappingSource().toString());
        }
    }

    public void testRuntimeSectionNonRuntimeType() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(builder -> builder.field("type", "keyword"));
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: No handler for type [keyword] declared on runtime field [field]", e.getMessage());
    }

    public void testRuntimeSectionHandlerNotFound() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(builder -> builder.field("type", "unknown"));
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: No handler for type [unknown] declared on runtime field [field]", e.getMessage());
    }

    public void testRuntimeSectionMissingType() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(builder -> {});
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: No type specified for runtime field [field]", e.getMessage());
    }

    public void testRuntimeSectionWrongFormat() throws IOException {
        XContentBuilder mapping = runtimeMapping(builder -> builder.field("field", "value"));
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: Expected map for runtime field [field] definition but got a java.lang.String",
            e.getMessage());
    }

    public void testRuntimeSectionRemainingField() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(builder -> builder.field("type", "test").field("unsupported", "value"));
        MapperParsingException e = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: Mapping definition for [field] has unsupported parameters:  " +
            "[unsupported : value]", e.getMessage());
    }

    private static class RuntimeFieldPlugin extends Plugin implements MapperPlugin {
        @Override
        public Map<String, RuntimeFieldType.Parser> getRuntimeFieldTypes() {
            return Collections.singletonMap("test", (name, node, parserContext) -> {
                Object prop1 = node.remove("prop1");
                Object prop2 = node.remove("prop2");
                return new RuntimeField(name, prop1 == null ? null : prop1.toString(), prop2 == null ? null : prop2.toString());
            });
        }
    }

    private static final class RuntimeField extends RuntimeFieldType {
        private final String prop1;
        private final String prop2;

        protected RuntimeField(String name, String prop1, String prop2) {
            super(name, Collections.emptyMap());
            this.prop1 = prop1;
            this.prop2 = prop2;
        }

        @Override
        public ValueFetcher valueFetcher(QueryShardContext context, SearchLookup searchLookup, String format) {
            return null;
        }

        @Override
        public String typeName() {
            return "test";
        }

        @Override
        public Query termQuery(Object value, QueryShardContext context) {
            return null;
        }

        @Override
        protected void doXContentBody(XContentBuilder builder, boolean includeDefaults) throws IOException {
            if (prop1 != null) {
                builder.field("prop1", prop1);
            }
            if (prop2 != null) {
                builder.field("prop2", prop2);
            }
        }

    }
}
