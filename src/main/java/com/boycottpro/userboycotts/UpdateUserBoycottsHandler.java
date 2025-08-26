package com.boycottpro.userboycotts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.ResponseMessage;
import com.boycottpro.userboycotts.models.CurrentReason;
import com.boycottpro.userboycotts.models.NewReason;
import com.boycottpro.userboycotts.models.UpdateReasonsForm;
import com.boycottpro.utilities.CauseValidator;
import com.boycottpro.utilities.CompanyValidator;
import com.boycottpro.utilities.JwtUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class UpdateUserBoycottsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateUserBoycottsHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public UpdateUserBoycottsHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, "Unauthorized");
            UpdateReasonsForm form = objectMapper.readValue(event.getBody(), UpdateReasonsForm.class);
            form.setUser_id(sub);
            System.out.println("UpdateReasonsForm = " + form.toString());
            String companyId = form.getCompany_id();
            String companyName = form.getCompany_name();
            CompanyValidator companyValidator = new CompanyValidator(this.dynamoDb,"companies");
            boolean validCompany = companyValidator.validateCompanyName(companyId,companyName);
            if(!validCompany) {
                System.out.println("company_name do not match!");
                throw new RuntimeException("not a valid company!");
            }
            boolean removalSuccess = removeSelectedReasons(sub, form);
            if (!removalSuccess) {
                throw new RuntimeException("Failed to remove selected reasons.");
            }
            Set<String> causeIds = new HashSet<>();
            for(CurrentReason reasonToRemove: form.getCurrentReasons()) {
                if(!reasonToRemove.isPersonal_reason() ) {
                    int index = reasonToRemove.getCompany_cause_id().indexOf("#");
                    String cause_id = reasonToRemove.getCompany_cause_id().substring(index+1);
                    if (reasonToRemove.isRemove() &&
                            reasonToRemove.getCompany_cause_id() != null &&
                            !reasonToRemove.getCompany_cause_id().isEmpty())
                    {
                        updateCauseCompanyStats(cause_id, companyId, companyName, null, -1);
                    } else if (!reasonToRemove.isRemove()) {
                        causeIds.add(cause_id);
                    }
                }

            }
            boolean additionSuccess = addNewReasons(sub, companyId, companyName,
                    form.getNewReasons(),
                    form.getPersonal_reason());
            if (!additionSuccess) {
                throw new RuntimeException("Failed to add new reasons.");
            }
            for(NewReason reasonToAdd: form.getNewReasons()) {
                String cause_id = reasonToAdd.getCause_id();
                if(!causeIds.contains(cause_id)) {
                    updateCauseCompanyStats(cause_id, companyId, companyName, reasonToAdd.getCause_desc(), 1);
                }
            }
            Set<NewReason> newlyFollowedCauses = getNewlyFollowedCauseIds(sub, form.getNewReasons());
            System.out.println("new causes size = " + newlyFollowedCauses.size());
            for (NewReason cause : newlyFollowedCauses) {
                System.out.println("inserting cause_id = " + cause.getCause_id());
                CauseValidator causeValidator = new CauseValidator(this.dynamoDb,"causes");
                boolean validCause = causeValidator.validateCauseDescription(cause.getCause_id(), cause.getCause_desc());
                if(!validCause) {
                    System.out.println("cause ID = " + cause.getCause_id() + " is not valid!");
                    continue;
                }
                insertUserCause(sub, cause);
                incrementCauseFollowerCount(cause);
            }

            // After all transactions, check if user is still boycotting this company
            if (!userIsBoycottingCompany(sub, companyId)) {
                decrementCompanyBoycottCount(companyId);
            }
            ResponseMessage message = new ResponseMessage(200,"Boycott reasons updated successfully.",
                    "no issues changing username");
            String responseBody = objectMapper.writeValueAsString(message);
            return response(200,responseBody);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseMessage message = new ResponseMessage(500,
                    "sorry, there was an error processing your request",
                    "Unexpected server error: " + e.getMessage());
            String responseBody = null;
            try {
                responseBody = objectMapper.writeValueAsString(message);
            } catch (JsonProcessingException ex) {
                System.out.println("JsonProcessingException");
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }
            return response(500,responseBody);
        }
    }
    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
    }
    private void updateCauseCompanyStats(String causeId, String companyId,
                                         String companyName, String causeDesc, int delta) {
        Map<String, AttributeValue> key = Map.of(
                "company_id", AttributeValue.fromS(companyId),
                "cause_id", AttributeValue.fromS(causeId)
        );

        UpdateItemRequest.Builder requestBuilder = UpdateItemRequest.builder()
                .tableName("cause_company_stats")
                .key(key);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":inc", AttributeValue.fromN(Integer.toString(delta)));

        if (delta > 0) {
            // Inserting or incrementing – might need to initialize the record
            expressionValues.put(":zero", AttributeValue.fromN("0"));
            expressionValues.put(":company_name", AttributeValue.fromS(companyName));
            expressionValues.put(":cause_desc", AttributeValue.fromS(causeDesc));

            requestBuilder.updateExpression(
                    "SET boycott_count = if_not_exists(boycott_count, :zero) + :inc, " +
                            "company_name = if_not_exists(company_name, :company_name), " +
                            "cause_desc = if_not_exists(cause_desc, :cause_desc)"
            );
        } else {
            // Decrementing – assume the record exists
            requestBuilder.updateExpression(
                    "SET boycott_count = boycott_count + :inc"
            );
        }

        requestBuilder.expressionAttributeValues(expressionValues);
        dynamoDb.updateItem(requestBuilder.build());
    }

    private boolean removeSelectedReasons(String userId, UpdateReasonsForm form) {
        try {
            List<WriteRequest> deletions = form.getCurrentReasons().stream()
                    .filter(CurrentReason::isRemove)
                    .map(reason -> WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                    .key(Map.of(
                                            "user_id", AttributeValue.fromS(userId),
                                            "company_cause_id", AttributeValue.fromS(reason.getCompany_cause_id())
                                    ))
                                    .build())
                            .build())
                    .collect(Collectors.toList());

            if (deletions.isEmpty()) return true;

            BatchWriteItemRequest batchDelete = BatchWriteItemRequest.builder()
                    .requestItems(Map.of("user_boycotts", deletions))
                    .build();

            dynamoDb.batchWriteItem(batchDelete);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean addNewReasons(String userId, String companyId, String companyName,
                                  List<NewReason> newReasons,
                                  String personalReason) {
        try {
            List<WriteRequest> additions = new ArrayList<>();
            System.out.println("company_id = " + companyId);
            // Add new cause-based reasons
            for (NewReason reason : newReasons) {
                String causeId = reason.getCause_id();
                String companyCauseId = companyId + "#" + causeId;
                Map<String, AttributeValue> item = Map.ofEntries(
                        Map.entry("user_id", AttributeValue.fromS(userId)),
                        Map.entry("company_cause_id", AttributeValue.fromS(companyCauseId)),
                        Map.entry("company_id", AttributeValue.fromS(companyId)),
                        Map.entry("company_name", AttributeValue.fromS(companyName)),
                        Map.entry("cause_id", AttributeValue.fromS(causeId)),
                        Map.entry("cause_desc", AttributeValue.fromS(reason.getCause_desc())),
                        Map.entry("timestamp", AttributeValue.fromS(Instant.now().toString()))
                );
                additions.add(WriteRequest.builder()
                        .putRequest(PutRequest.builder().item(item).build())
                        .build());
            }

            // Add new personal reason if present
            if (personalReason != null && !personalReason.isBlank()) {
                String companyCauseId = personalReason + "#" + companyId ;
                Map<String, AttributeValue> item = Map.ofEntries(
                        Map.entry("user_id", AttributeValue.fromS(userId)),
                        Map.entry("company_cause_id", AttributeValue.fromS(companyCauseId)),
                        Map.entry("company_id", AttributeValue.fromS(companyId)),
                        Map.entry("company_name", AttributeValue.fromS(companyName)),
                        Map.entry("personal_reason", AttributeValue.fromS(personalReason)),
                        Map.entry("timestamp", AttributeValue.fromS(Instant.now().toString()))
                );
                additions.add(WriteRequest.builder()
                        .putRequest(PutRequest.builder().item(item).build())
                        .build());
            }

            if (additions.isEmpty()) return true;

            BatchWriteItemRequest batchInsert = BatchWriteItemRequest.builder()
                    .requestItems(Map.of("user_boycotts", additions))
                    .build();

            dynamoDb.batchWriteItem(batchInsert);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private Set<NewReason> getNewlyFollowedCauseIds(String userId, List<NewReason> newCauseIds) {
        QueryRequest request = QueryRequest.builder()
                .tableName("user_causes")
                .keyConditionExpression("user_id = :uid")
                .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(userId)))
                .projectionExpression("cause_id")
                .build();

        QueryResponse response = dynamoDb.query(request);
        Set<String> existingCauses = response.items().stream()
                .map(item -> item.get("cause_id").s())
                .collect(Collectors.toSet());

        return newCauseIds.stream()
                .filter(id -> !existingCauses.contains(id.getCause_id()))
                .collect(Collectors.toSet());
    }

    private void insertUserCause(String userId, NewReason cause) {
        String now = Instant.now().toString();
        // you may need to fetch cause_desc separately
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("user_causes")
                .item(Map.of(
                        "user_id", AttributeValue.fromS(userId),
                        "cause_id", AttributeValue.fromS(cause.getCause_id()),
                        "cause_desc", AttributeValue.fromS(cause.getCause_desc()), // fill in actual desc
                        "timestamp", AttributeValue.fromS(now)
                ))
                .build();
        dynamoDb.putItem(putRequest);
    }

    private void incrementCauseFollowerCount(NewReason cause) {
        System.out.println("incrementing the causes record for cause_desc = " + cause.getCause_desc());
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("causes")
                .key(Map.of("cause_id", AttributeValue.fromS(cause.getCause_id())))
                .updateExpression("SET follower_count = if_not_exists(follower_count, :zero) + :inc")
                .expressionAttributeValues(Map.of(
                        ":inc", AttributeValue.fromN("1"),
                        ":zero", AttributeValue.fromN("0")
                ))
                .build();
        dynamoDb.updateItem(updateRequest);
    }

    private boolean userIsBoycottingCompany(String userId, String companyId) {
        QueryRequest request = QueryRequest.builder()
                .tableName("user_boycotts")
                .keyConditionExpression("user_id = :uid")
                .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(userId)))
                .projectionExpression("company_id")
                .build();

        QueryResponse response = dynamoDb.query(request);
        return response.items().stream()
                .anyMatch(item -> companyId.equals(item.get("company_id").s()));
    }

    private void decrementCompanyBoycottCount(String companyId) {
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("companies")
                .key(Map.of("company_id", AttributeValue.fromS(companyId)))
                .updateExpression("SET boycott_count = if_not_exists(boycott_count, :zero) - :dec")
                .expressionAttributeValues(Map.of(
                        ":dec", AttributeValue.fromN("1"),
                        ":zero", AttributeValue.fromN("0")
                ))
                .build();
        dynamoDb.updateItem(updateRequest);
    }

}