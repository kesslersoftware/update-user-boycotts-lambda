package com.boycottpro.userboycotts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.userboycotts.UpdateUserBoycottsHandler;
import com.boycottpro.userboycotts.models.CurrentReason;
import com.boycottpro.userboycotts.models.NewReason;
import com.boycottpro.userboycotts.models.UpdateReasonsForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserBoycottsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @InjectMocks
    private UpdateUserBoycottsHandler handler;

    private ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void testHandleRequest_successfulUpdate() throws JsonProcessingException {
        String userId = "user123";
        String companyId = "company456";
        String companyName = "companyName";
        String causeId1 = "cause1";
        String causeId2 = "cause2";

        UpdateReasonsForm form = new UpdateReasonsForm();
        form.setUser_id(userId);
        form.setCompany_id(companyId);
        form.setCompany_name(companyName);
        form.setCurrentReasons(List.of(
                new CurrentReason(companyId + "#" + causeId1, false, true),
                new CurrentReason(companyId + "#" + causeId2, false, false)
        ));
        form.setNewReasons(List.of(new NewReason("cause3","cause_desc")));
        Map<String, AttributeValue> companyItem = Map.of(
                "company_name", AttributeValue.fromS(companyName)
        );

        GetItemResponse companyMockResponse = GetItemResponse.builder()
                .item(companyItem)
                .build();
        Map<String, AttributeValue> causeItem = Map.of(
                "cause_desc", AttributeValue.fromS("cause_desc")
        );

        GetItemResponse causeMockResponse = GetItemResponse.builder()
                .item(causeItem)
                .build();

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(companyMockResponse)
                .thenReturn(causeMockResponse);
        form.setCurrentPersonalReason("personal reason");

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(form));

        // Mock query for userIsAlreadyFollowing (returns empty)
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(Collections.emptyList()).build());

        // Mock batchWriteItem for deletes/inserts
        when(dynamoDb.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(BatchWriteItemResponse.builder().build());

        // Mock updateItem for cause and company increments
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mock(Context.class));

        assertEquals(200, response.getStatusCode());

        verify(dynamoDb, atLeastOnce()).query(any(QueryRequest.class));
        verify(dynamoDb, atLeastOnce()).batchWriteItem(any(BatchWriteItemRequest.class));
        verify(dynamoDb, atLeastOnce()).updateItem(any(UpdateItemRequest.class));
    }
}
