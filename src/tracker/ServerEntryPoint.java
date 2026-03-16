package tracker;

import java.util.Scanner;

public class ServerEntryPoint {
    public static void main(String[] args) {

        Tracker tracker = new Tracker();

        tracker.startAsync();

        @SuppressWarnings("resource")
        Scanner sc = new Scanner(System.in);
        System.out.println("Tracker running. Press X then Enter to exit.");

        while (true) {
            String input = sc.nextLine();
            if (input.equalsIgnoreCase("X")) {
                tracker.stop();
                break;
            }
        }
        System.out.println("Server exiting.");
    }
}
