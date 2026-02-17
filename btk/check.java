import java.io.*;

public class check {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Kullanim: java check <test_inputs.txt>");
            return;
        }
        check runner = new check();
        BufferedReader reader = new BufferedReader(new FileReader(args[0]));
        PrintWriter out = new PrintWriter(new FileWriter("output.txt"));
        String line;
        int testNo = 1;
        out.println("=".repeat(60));
        out.println("  checkCpSenderAddressValidity TEST SONUCLARI");
        out.println("=".repeat(60));
        out.printf("%-5s | %-20s | %-8s%n", "No", "Input", "Sonuc");
        out.println("-".repeat(60));
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                out.println("-".repeat(60));
                out.println("  " + line.substring(1).trim());
                out.println("-".repeat(60));
                continue;
            }
            if (line.trim().isEmpty())
                continue;
            boolean result = runner.checkCpSenderAddressValidity(line);
            out.printf("%-5d | %-20s | %-8s%n", testNo, "\"" + line + "\"", result ? "VALID" : "INVALID");
            testNo++;
        }
        reader.close();
        out.println("=".repeat(60));
        out.println("Toplam test: " + (testNo - 1));
        out.close();
        System.out.println("Sonuclar output.txt dosyasina yazildi.");
    }

    public boolean checkCpSenderAddressValidity(String senderAddress) {
        if (senderAddress.length() < 3 || senderAddress.length() > 11) {
            return false;
        }
        // Leading and trailing spaces case
        if (senderAddress.length() != senderAddress.trim().length()) {
            return false;
        }
        // Double spaces case
        if (senderAddress.contains("  ")) {
            return false;
        }
        // All digits case
        if (isNumber(senderAddress)) {
            if (senderAddress.length() != 3) {
                return false;
            }
            if (senderAddress.charAt(0) != '1') {
                return false;
            }
            if (senderAddress.charAt(1) == '0') {
                return false;
            }
            return true;
        }
        // Alphanumeric case
        if (!senderAddress.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ')) {
            return false;
        }
        // Suspicious characters case
        if (containsOnlySuspiciousCharactersAndNumbers(senderAddress)) {
            return false;
        }

        return true;
    }

    public boolean isNumber(String str) {
        return str.chars().allMatch(Character::isDigit);
    }

    public boolean containsOnlySuspiciousCharactersAndNumbers(String str) {
        String suspiciousChars = "OoIlEASsbTBqg0123456789";
        return str.chars().allMatch(c -> suspiciousChars.indexOf(c) >= 0);

    }

}