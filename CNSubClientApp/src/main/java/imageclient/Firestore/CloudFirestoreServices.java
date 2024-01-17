package imageclient.Firestore;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import java.util.*;

public class CloudFirestoreServices {

    private Firestore db;
    private String currentCollection = "objectImageDetector";

    public CloudFirestoreServices() {

        GoogleCredentials credentials;

        try {

            // Acede às credenciais da google a partir da variável de ambiente GOOGLE_APPLICATION_CREDENTIALS
            credentials = GoogleCredentials.getApplicationDefault();

            this.db = FirestoreOptions.newBuilder().setCredentials(credentials).build().getService();

        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    public void insertMessage (List<ObjectInfo> objInfoList, String blobName, String docID) throws Exception {

        // Obtém a referência para a coleção
        CollectionReference colRef = db.collection(currentCollection);

        // Obtém a referência para o documento identificado pelo "docID"
        DocumentReference docRef = colRef.document(docID);

        // Variável para montar o modelo da firestore
        ImageProcessor imgProcessor = new ImageProcessor();

        // Guarda a informação da imagem original que já se encontrava na firestore
        OriginalData originalData = new OriginalData();
        originalData.blob = docRef.get().get().getData().get("blob").toString();
        originalData.bucket = docRef.get().get().getData().get("bucket").toString();
        imgProcessor.originalData = originalData;

        // Insere a informação dos objetos detetados no respetivo formato da Firestore
        imgProcessor.link = "https://storage.cloud.google.com/cn-2122-g03-trabalho_final/annotated-" + blobName;
        imgProcessor.processDate = new Date();

        // Cria a coleção objects
        CollectionReference docColRef = docRef.collection("objects");

        // Para cada objeto encontrado cria um documento e insere-lhe o nome e o grau de certeza
        objInfoList.forEach( obj -> {
            DocumentReference docColDocRef = docColRef.document();

            ObjectInfo objectInfo = new ObjectInfo();
            objectInfo.name = obj.name;
            objectInfo.assuranceDegree = obj.assuranceDegree;

            docColDocRef.create(objectInfo);
        });

        // Altera o documento com toda a informação acerca da imagem
        ApiFuture<WriteResult> result = docRef.set(imgProcessor);

        while(result.isCancelled() ) {  this.db.shutdownNow(); }

        while(!result.isDone()) {   }

        System.out.println("Processed annotated image....");
        this.db.shutdown();

        System.out.println(result);
    }

}
