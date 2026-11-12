public class Main {
    public static void main(String[] args) {
        String[] options = {
            "Member 1: Station Management",
            "Member 2: Train Management",
            "Member 3: Train Call Management",
            "Member 4: Passenger Flow Management",
            "Exit"
        };
        while (true) {
            String choice = Utils.displayMenu("--- LRT Database Application ---", options);
            
            switch (choice) {
                case "Member 1: Station Management" -> new Member1().menu();
                case "Member 2: Train Management" -> new Member2().menu();
                case "Member 3: Train Call Management" -> new Member3().menu();
                case "Member 4: Passenger Flow Management" -> new Member4().menu();
                case "Exit" -> {
                    System.out.println("Exiting application...");
                    return;
                }
            }
        }
    }
}
