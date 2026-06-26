package io.batchadmin.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

class GenericJsonItemWriterTest {

    @Test
    void delegatesEachChunkToTheSink() throws Exception {
        List<String> captured = new ArrayList<>();
        boolean[] closed = {false};
        JsonDocumentSink sink = new JsonDocumentSink() {
            @Override
            public void write(List<String> jsonDocuments) {
                captured.addAll(jsonDocuments);
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };

        GenericJsonItemWriter writer = new GenericJsonItemWriter(sink);
        writer.write(Chunk.of("{\"a\":1}", "{\"b\":2}"));
        writer.write(Chunk.of("{\"c\":3}"));
        writer.close();

        assertThat(captured).containsExactly("{\"a\":1}", "{\"b\":2}", "{\"c\":3}");
        assertThat(closed[0]).isTrue();
    }

    @Test
    void emptyChunkIsANoOp() throws Exception {
        List<String> captured = new ArrayList<>();
        GenericJsonItemWriter writer = new GenericJsonItemWriter(captured::addAll);
        writer.write(new Chunk<>());
        assertThat(captured).isEmpty();
    }
}
