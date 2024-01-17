package grpcservice;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CloudStorageServices {

    public static String DEFAULT_BUCKET_ID = "cn-2122-g03-trabalho_final";
    private String projectId;
    private Storage storage;

    public CloudStorageServices(String projectId) {

        GoogleCredentials credentials;

        try {
            // Acede às credenciais da google a partir da variável de ambiente GOOGLE_APPLICATION_CREDENTIALS
            credentials = GoogleCredentials.getApplicationDefault();

            this.projectId = projectId;
            this.storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials).build().getService();

        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    public void storeImage(BlobInfo imageInfo, ByteString image) {
        storage.create(imageInfo, image.toByteArray());
    }

    public String getProjectId() {
        return projectId;
    }

    public ChunkingServices getChunkingServices(BlobInfo imageInfo) {
        //Cria um serviço de chunks com canal para o cloud storage
        return new ChunkingServices(storage.writer(imageInfo));
    }


    public static class ChunkingServices {

        private final WriteChannel channel;

        //Cria um canal que irá ler Strings de bytes (bocados da imagem) e escrevê-los no canal passado no construtor
        private ChunkingServices(WriteChannel channel) {
            this.channel = channel;
        }

        public void storeImageChunk(ByteString chunk) throws IOException {
            channel.write(chunk.asReadOnlyByteBuffer());
        }

        public void closeChannel() throws IOException {
            channel.close();
        }
    }

}
