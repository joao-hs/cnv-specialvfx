package pt.ulisboa.tecnico.cnv.webserver;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import pt.ulisboa.tecnico.cnv.javassist.tools.SpecialVFXTool;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MSSWriter {
    private static final Regions AWS_REGION = Regions.EU_WEST_3;
    private static final String TABLE_NAME = "mss";

    private final AmazonDynamoDB dynamoDB;

    private static MSSWriter instance = null;

    private int retrievingInterval = 15000;

    private MSSWriter() {
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build();

        CreateTableRequest createTableRequest = new CreateTableRequest()
                .withTableName(TABLE_NAME)
                .withKeySchema(new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition().withAttributeName("id").withAttributeType(ScalarAttributeType.N))
                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L));

        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);

        try {
            TableUtils.waitUntilActive(dynamoDB, TABLE_NAME);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static MSSWriter getInstance() {
        if (instance == null) {
            instance = new MSSWriter();
        }
        return instance;
    }

    public void setInstrumentationRetrievingInterval(int interval) {
        this.retrievingInterval = interval;
    }

    public void start() {
        new Thread(() -> {
            System.out.println("Starting instrumentation retriever");
            System.out.println("requestId,nmethods,nblocks,ninsts");
            while (true) {
                try {
                    Thread.sleep(this.retrievingInterval);
                    this.handleLogEntries(SpecialVFXTool.flushLog());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private synchronized void handleLogEntries(String[] logEntries) {
        // Could be BatchWriteRequest, but it provides all or nothing semantics, which won't be useful
        Stream<String> stream = Stream.of(logEntries);
        if (WebServer.LOCALHOST) {
            File localDb = new File("/tmp/SpecialVFX-local-mss.db");
            try {
                localDb.createNewFile();
                FileWriter writer = new FileWriter(localDb, true);
                stream.forEach(entry -> {
                    try {
                        System.out.println(entry);
                        writer.write(entry + "\n");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            stream.map(entry -> new PutItemRequest(TABLE_NAME, newItemFromLogEntry(entry))).forEach(dynamoDB::putItem);
        }
    }

    private Map<String, AttributeValue> newItemFromLogEntry(String logEntry) {
        Map<String, AttributeValue> item = new HashMap<>();
        String[] splitEntry = logEntry.split("\\|");
        item.put("id", new AttributeValue().withN(splitEntry[0]));
        item.put("type", new AttributeValue().withN(splitEntry[1]));
        item.put("features", new AttributeValue(splitEntry[2]));
        item.put("cost", new AttributeValue().withN(splitEntry[3]));
        return item;
    }
}
