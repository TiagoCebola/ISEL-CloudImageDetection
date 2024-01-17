package imageclient;
;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import java.util.*;

public class WorkerApp {

    static String PROJECT_ID = "cn2122-t1-g03";

    public static Subscriber subscribeMessagesWorkers(String projectID, String subscriptionName) {

        // Acede à subscrição passada por parâmetro
        ProjectSubscriptionName projSubscriptionName = ProjectSubscriptionName.of(projectID, subscriptionName);

        // Subscreve a subscrição associada ao topico
        Subscriber subscriber = Subscriber.newBuilder(projSubscriptionName, new MonitorMessageReceiveHandler()).build();

        // Inicializa o subscritor
        subscriber.startAsync().awaitRunning();

        return subscriber;
    }

    public static void main(String[] args) {

        try {
            Subscriber subscriber = subscribeMessagesWorkers(PROJECT_ID, "detectionworkers-sub");
            System.out.println("Subscribed to detectionworkers-sub with success!\n");

            Scanner scan= new Scanner(System.in);
            System.out.println("To leave insert the command: stop");
            while(scan.nextLine() != "stop") {}

            subscriber.stopAsync();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
