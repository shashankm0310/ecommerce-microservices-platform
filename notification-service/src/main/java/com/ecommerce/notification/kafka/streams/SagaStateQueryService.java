package com.ecommerce.notification.kafka.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaStateQueryService {

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;
    private final ObjectMapper objectMapper;

    public Optional<Map<String, Object>> getSagaState(String orderId) {
        try {
            KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();
            if (kafkaStreams == null || kafkaStreams.state() != KafkaStreams.State.RUNNING) {
                log.warn("Kafka Streams not running, state: {}",
                        kafkaStreams != null ? kafkaStreams.state() : "null");
                return Optional.empty();
            }

            ReadOnlyKeyValueStore<String, String> store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(
                            OrderSagaStreamsTopology.SAGA_STORE,
                            QueryableStoreTypes.keyValueStore()
                    )
            );

            String stateJson = store.get(orderId);
            if (stateJson == null) {
                return Optional.empty();
            }

            Map<String, Object> state = objectMapper.readValue(stateJson, Map.class);
            return Optional.of(state);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse saga state for order {}: {}", orderId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query saga state for order {}: {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }
}
