package grpcservice.Firestore;

import cnimageservice.FileResp;
import cnimageservice.ImgWithObjDetected;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import java.util.*;

public class CloudFirestoreServices {

    private Firestore db;
    private final String currentCollection;

    public CloudFirestoreServices(String collectionName) {

        GoogleCredentials credentials = null;
        this.currentCollection = collectionName;

        try {
            // Acede às credenciais da google a partir da variável de ambiente GOOGLE_APPLICATION_CREDENTIALS
            credentials = GoogleCredentials.getApplicationDefault();

            this.db = FirestoreOptions.newBuilder().setCredentials(credentials).build().getService();
        }

        catch (Exception e) {
            System.out.println(e);
        }
    }

    public void close() throws Exception {
        // Fecha a ligação à Firestore
        db.close();
    }

    public String generateCollectionDoc(String bucketName, String imageType) {

        // Obtém a referência para a coleção
        CollectionReference colRef = db.collection(currentCollection);

        // Obtém a referência para o documento (Como não recebe nada como parâmetro gera um indicador único)
        DocumentReference docRef = colRef.document();

        // Cria a informação acerca da imagem original para inserir no documento
        OriginalData originalData = new OriginalData();
        originalData.blob = docRef.getId() + imageType;
        originalData.bucket = bucketName;

        docRef.create(originalData); // Cria o documento com a informação passada como parâmetro

        return docRef.getId();
    }

    public ImgWithObjDetected readDocumentByID(String ID) throws Exception {

        // Obtém a referência para o documento
        DocumentReference docRef = db.collection(currentCollection).document(ID);

        // Obtém uma cópia instantânea do documento referenciado
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if ( document.exists() ) {
            // Obtém o link para a imagem alterada
            String link = document.getData().get("link").toString();

            // Inicializa o construtor da variável do contrato com o link obtido
            ImgWithObjDetected.Builder imgWithObjDetected = ImgWithObjDetected.newBuilder().setDetectedImage(link);

            CollectionReference docColRef = docRef.collection("objects");
            Iterable<DocumentReference> docList = docColRef.listDocuments();
            docList.forEach( colDocRef -> {
                try {

                    ApiFuture<DocumentSnapshot> docFuture = colDocRef.get();
                    DocumentSnapshot docSnapshot = docFuture.get();

                    imgWithObjDetected.addObjectNames(docSnapshot.getData().get("name").toString());
                    imgWithObjDetected.addAssuranceDegrees(Double.parseDouble(docSnapshot.getData().get("assuranceDegree").toString()));

                } catch (Exception e) {
                    e.printStackTrace();
                }

            });


            // Retorna a variável "construída"
            return imgWithObjDetected.build();

        }
        else  {
            throw new Exception("Document does not exists");
        }

    }

    /**
     * TODO
     Obter todos os nomes de ficheiros armazenados no sistema entre duas datas, que contêm
     um objeto com determinado nome e com um grau de certeza na deteção acima de t
     **/
    public FileResp composeQueryWithIndex(String dt1, String dt2, String objectName, double assuranceDegree) throws Exception {

        ApiFuture<QuerySnapshot> querySnapshot1 = db.collection(currentCollection)
                .whereGreaterThan("processDate", new Date(dt1))
                .whereLessThan("processDate", new Date(dt2)).get();

        FileResp.Builder fileResp = FileResp.newBuilder();
        for (DocumentSnapshot doc : querySnapshot1.get().getDocuments()) {
            ApiFuture<QuerySnapshot> querySnapshot2 = db.collection(currentCollection).document(doc.getId()).collection("objects")
                    .whereEqualTo("name", objectName)
                    .whereGreaterThan("assuranceDegree", assuranceDegree).get();

            var x = querySnapshot2.get().getDocuments().size();
            if(querySnapshot2.get().getDocuments().size() > 0) {
                String objName = doc.getData().get("link").toString();
                fileResp.addFileName(objName);
            }
        }

        return fileResp.build();
    }
}
