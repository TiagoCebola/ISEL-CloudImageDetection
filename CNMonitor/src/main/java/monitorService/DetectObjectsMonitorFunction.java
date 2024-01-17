package monitorService;

import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.InstanceGroupManagersClient;
import com.google.cloud.compute.v1.ListManagedInstancesInstanceGroupManagersRequest;
import com.google.cloud.compute.v1.ManagedInstance;
import com.google.cloud.compute.v1.Operation;
import com.google.cloud.firestore.*;
import com.google.cloud.functions.*;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class DetectObjectsMonitorFunction implements BackgroundFunction<PSmessage>  {

    private static final String PROJECT_ID = "cn2122-t1-g03";
    private static final String ZONE = "europe-southwest1-a";
    private static final String INSTANCE_GROUP = "instance-group-workers";
    private static final Firestore db = initFirestore();
    private static String  docID = "Monitor";
    private static final String currentCollection = "SubmitedVMRequests";
    private static final double x = 0.07;
    private static final double y = 0.03;

    private static Firestore initFirestore() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirestoreOptions options = FirestoreOptions
                    .newBuilder()
                    .setCredentials(credentials)
                    .build();

            Firestore db = options.getService();
            return db;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static int listManagedInstanceGroupVMs() throws IOException {
        InstanceGroupManagersClient managersClient = InstanceGroupManagersClient.create();
        ListManagedInstancesInstanceGroupManagersRequest request =
                ListManagedInstancesInstanceGroupManagersRequest.newBuilder()
                        .setInstanceGroupManager(INSTANCE_GROUP)
                        .setProject(PROJECT_ID)
                        .setReturnPartialSuccess(true)
                        .setZone(ZONE)
                        .build();

        int counterrr = 0 ;
        System.out.println("Instances of instance group: " + INSTANCE_GROUP);
        for (ManagedInstance instance :
                managersClient.listManagedInstances(request).iterateAll()) {
            counterrr ++;
        }

        return counterrr;
    }

    static void resizeManagedInstanceGroup( int newSize) throws IOException, InterruptedException, ExecutionException {
        System.out.println("================== Resizing instance group");
        InstanceGroupManagersClient managersClient = InstanceGroupManagersClient.create();
        OperationFuture<Operation, Operation> result = managersClient.resizeAsync(
                PROJECT_ID,
                ZONE,
                INSTANCE_GROUP,
                newSize
        );
        Operation oper = result.get();
        System.out.println("Resizing with status " + oper.getStatus());
    }

    @Override
    public void accept(PSmessage pSmessage, Context context) throws Exception {
        // Obtém a referência para o documento
        DocumentReference docRef = db.collection(currentCollection).document(docID);

        // Obtém uma cópia instantânea do documento referenciado
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if ( document.exists() ) {
            // Obtém o link para a imagem alterada
            int counter = Integer.parseInt(document.getData().get("counter").toString());
            long tRef = Long.parseLong(document.getData().get("Tref").toString());
            long tRequest = System.currentTimeMillis();

            counter++;
            HashMap<String, Object> updateDoc = new HashMap<>();

            if (tRef < 0 ){
                updateDoc.put("Tref", tRequest);
                updateDoc.put("counter", counter);
            }
            else if ((tRequest - tRef) >= 60000  ){
                int k = listManagedInstanceGroupVMs();
                double ritmo = counter / ((tRequest - tRef) / 100);

                if(ritmo > x && k < 4 ) k++;
                else if (ritmo < y  && k > 1) k--;

                counter = 0;

                resizeManagedInstanceGroup(k);
                updateDoc.put("Tref", tRequest);
                updateDoc.put("counter", counter);
            }
            else {
                updateDoc.put("counter", counter);
            }
            docRef.update(updateDoc);
        }
        else  {
            throw new Exception("Document does not exists");
        }
    }
}
