package com.redis.riot.file;

import java.io.IOException;

import org.springframework.batch.core.Job;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.item.json.JsonObjectMarshaller;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redis.riot.core.AbstractExport;
import com.redis.riot.core.RiotExecutionContext;
import com.redis.riot.core.RiotExecutionException;
import com.redis.riot.core.StepBuilder;
import com.redis.riot.file.resource.JsonResourceItemWriter;
import com.redis.riot.file.resource.JsonResourceItemWriterBuilder;
import com.redis.riot.file.resource.XmlResourceItemWriter;
import com.redis.riot.file.resource.XmlResourceItemWriterBuilder;
import com.redis.spring.batch.KeyValue;
import com.redis.spring.batch.ValueType;

import io.lettuce.core.codec.StringCodec;

public class FileDumpExport extends AbstractExport<String, String> {

    public static final String DEFAULT_ELEMENT_NAME = "record";

    public static final String DEFAULT_ROOT_NAME = "root";

    public static final String DEFAULT_LINE_SEPARATOR = System.getProperty("line.separator");

    private String file;

    private FileOptions fileOptions = new FileOptions();

    private boolean append;

    private String rootName = DEFAULT_ROOT_NAME;

    private String elementName = DEFAULT_ELEMENT_NAME;

    private String lineSeparator = DEFAULT_LINE_SEPARATOR;

    private FileDumpType type;

    public FileDumpExport() {
        super(StringCodec.UTF8);
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setFileOptions(FileOptions fileOptions) {
        this.fileOptions = fileOptions;
    }

    public void setType(FileDumpType type) {
        this.type = type;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    @Override
    protected Job job(RiotExecutionContext executionContext) {
        StepBuilder<KeyValue<String>, KeyValue<String>> step = createStep();
        step.name(getName());
        step.reader(reader(executionContext));
        step.writer(writer());
        return jobBuilder().start(step.build()).build();
    }

    private ItemWriter<KeyValue<String>> writer() {
        WritableResource resource;
        try {
            resource = FileUtils.outputResource(file, fileOptions);
        } catch (IOException e) {
            throw new RiotExecutionException("Could not open file for writing: " + file, e);
        }
        if (dumpType(resource) == FileDumpType.XML) {
            return xmlWriter(resource);
        }
        return jsonWriter(resource);
    }

    private FileDumpType dumpType(WritableResource resource) {
        if (type == null) {
            return FileUtils.dumpType(resource);
        }
        return type;
    }

    private JsonResourceItemWriter<KeyValue<String>> jsonWriter(Resource resource) {
        JsonResourceItemWriterBuilder<KeyValue<String>> jsonWriterBuilder = new JsonResourceItemWriterBuilder<>();
        jsonWriterBuilder.name("json-resource-item-writer");
        jsonWriterBuilder.append(append);
        jsonWriterBuilder.encoding(fileOptions.getEncoding());
        jsonWriterBuilder.jsonObjectMarshaller(new JacksonJsonObjectMarshaller<>(objectMapper()));
        jsonWriterBuilder.lineSeparator(lineSeparator);
        jsonWriterBuilder.resource(resource);
        jsonWriterBuilder.saveState(false);
        return jsonWriterBuilder.build();
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    private XmlResourceItemWriter<KeyValue<String>> xmlWriter(Resource resource) {
        XmlResourceItemWriterBuilder<KeyValue<String>> xmlWriterBuilder = new XmlResourceItemWriterBuilder<>();
        xmlWriterBuilder.name("xml-resource-item-writer");
        xmlWriterBuilder.append(append);
        xmlWriterBuilder.encoding(fileOptions.getEncoding());
        xmlWriterBuilder.xmlObjectMarshaller(xmlMarshaller());
        xmlWriterBuilder.lineSeparator(lineSeparator);
        xmlWriterBuilder.rootName(rootName);
        xmlWriterBuilder.resource(resource);
        xmlWriterBuilder.saveState(false);
        return xmlWriterBuilder.build();
    }

    private JsonObjectMarshaller<KeyValue<String>> xmlMarshaller() {
        XmlMapper mapper = new XmlMapper();
        mapper.setConfig(mapper.getSerializationConfig().withRootName(elementName));
        JacksonJsonObjectMarshaller<KeyValue<String>> marshaller = new JacksonJsonObjectMarshaller<>();
        marshaller.setObjectMapper(mapper);
        return marshaller;
    }

    @Override
    protected ValueType getValueType() {
        return ValueType.STRUCT;
    }

}
