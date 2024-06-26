package com.virtual_bank_g66.demo;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.event.ActionEvent;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

/**
 * The DepositAndWithdrawalController class handles deposit and withdrawal transactions
 * for both current and saving accounts. It validates user input, processes transactions,
 * and logs the transaction details.
 *
 * @version 1.0 April 10th, 2024 - fulfill basic requirements to deposit money and withdraw money from both current and saving account.
 * @version 2.0 April 24th, 2024 - introduce utility classes to promote code reuse
 * @version 3.0 May 15th, 2024 - Modified withdrawal functionality as per assistant's suggestions (add timeLimit)
 * @author Jiabo Tong
 * @author Jiameng Chen
 */
public class DepositAndWithdrawalController {

    @FXML
    protected TextField amountField;

    @FXML
    protected TextField descriptionField;

    @FXML
    protected PasswordField passwordField;

    @FXML
    protected Button btnBack;

    @FXML
    protected ComboBox<String> roleComboBox;

    @FXML
    protected Text ServiceType;

    Utils Utils = new Utils();
    FileUtil FileUtil = new FileUtil();

    /**
     * Handles the confirm button click event. Validates the user input,
     * updates account details, and logs the transaction if validation is successful.
     *
     * @param event the action event triggered by clicking the confirm button
     */
    @FXML
    protected void handleConfirm(ActionEvent event) {

        UserInfoBean userInfo = UserInfoBean.getInstance();
        String serviceType = ServiceType.getText();
        String accountType = roleComboBox.getValue();
        String description = descriptionField.getText();
        String password = passwordField.getText();

        if (serviceType == null || serviceType.isEmpty() ||
                accountType == null || accountType.trim().isEmpty() ||
                description == null || description.isEmpty() ||
                password == null || password.isEmpty() ||
                amountField.getText() == null || amountField.getText().isEmpty()) {
            Utils.showAlert("Error", "There are invalid inputs, please enter all required information.", Alert.AlertType.ERROR);
        } else {

            try {
                float amount = Float.parseFloat(amountField.getText());


                if (!password.equals(userInfo.getPassword())) {
                    Utils.showAlert("Error", "Original password is incorrect.", Alert.AlertType.ERROR);
                    passwordField.clear();
                    descriptionField.clear();
                    amountField.clear();
                    return;
                }

                if (!updateAccountDetails(userInfo.getID(), serviceType, accountType, amount)) {
                    passwordField.clear();
                    descriptionField.clear();
                    amountField.clear();
//                    Utils.showAlert("Error", "Failed to update account details.", Alert.AlertType.ERROR);
                    return;
                }

                logTransaction(userInfo.getID(), LocalDateTime.now(), amount, accountType, serviceType, description);
                Utils.showAlert("Congratulations", "Transaction successful.", Alert.AlertType.INFORMATION);
                passwordField.clear();
                descriptionField.clear();
                amountField.clear();
            } catch (NumberFormatException e) {
                Utils.showAlert("Error", "Invalid amount. Please enter a valid number.", Alert.AlertType.ERROR);
            }
        }

    }

    /**
     * Updates the account details based on the transaction type (deposit or withdrawal)
     * and account type (current or saving).
     *
     * @param userId the ID of the user
     * @param serviceType the type of service (deposit or withdrawal)
     * @param accountType the type of account (current or saving)
     * @param amount the amount to be deposited or withdrawn
     * @return true if the account details were successfully updated, false otherwise
     */
    private boolean updateAccountDetails(String userId, String serviceType, String accountType, float amount) {
        List<String> fileContent = new ArrayList<>();
        boolean accountExist = false;

        try (BufferedReader br = new BufferedReader(new FileReader(Utils.CSV_FILE_PATH_moneyInfo))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    fileContent.add(line);
                    continue;
                }
                String[] values = line.split(",");
                if (values[0].equals(userId)) {
                    accountExist = true;
                    if (!processAccountLine(values, serviceType, accountType, amount)) {
                        return false;
                    }
                    line = String.join(",", values);
                }
                fileContent.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert("Error", "An error occurred while processing your request.", Alert.AlertType.ERROR);
            return false;
        }

        if (!accountExist) {
            Utils.showAlert("Error", "Account does not exist.", Alert.AlertType.ERROR);
            return false;
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(Utils.CSV_FILE_PATH_moneyInfo))) {
            for (String s : fileContent) {
                bw.write(s + System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert("Error", "An error occurred while saving your data.", Alert.AlertType.ERROR);
            return false;
        }

        return true;
    }

    /**
     * Processes the account line based on the transaction type and account type.
     *
     * @param values the account details
     * @param serviceType the type of service (deposit or withdrawal)
     * @param accountType the type of account (current or saving)
     * @param amount the amount to be deposited or withdrawn
     * @return true if the account line was successfully processed, false otherwise
     */
    private boolean processAccountLine(String[] values, String serviceType, String accountType, float amount) {
        float currentAmount = Float.parseFloat(values[1]);
        float savingAmount = Float.parseFloat(values[2]);
        float limit = Float.parseFloat(values[3]);
        String timeLimit = values[5];
        String initialDateStr = values[6];
        String MatureDateStr = values[7];

        LocalDateTime now = LocalDateTime.now();

        if ("withdrawal".equalsIgnoreCase(serviceType)) {
            if (amount > limit && limit != 0) {
                Utils.showAlert("Sorry", "Exceed your account limitation.", Alert.AlertType.INFORMATION);
                return false;
            }

            if ("Current".equals(accountType) && currentAmount >= amount) {
                values[1] = String.format("%.2f", currentAmount - amount);
            } else if ("Saving".equals(accountType)) {
                if(values[7].equals(" ")){
                    Utils.showAlert("Sorry", "Insufficient funds.", Alert.AlertType.INFORMATION);
                    return false;
                }

                LocalDateTime expireDate = LocalDateTime.parse(values[7], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                if (now.isBefore(expireDate)) {
                    long daysUntilMatured = java.time.Duration.between(now, expireDate).toDays();
                    Utils.showAlert("Sorry", "Savings account funds are not yet available for withdrawal. Matured time is: " + values[7] + ". There are " + daysUntilMatured + " days remaining.", Alert.AlertType.INFORMATION);
                    return false;
                } else if (savingAmount >= amount) {
                    values[2] = String.format("%.2f", savingAmount - amount);
                }
            } else {
                Utils.showAlert("Sorry", "Insufficient funds.", Alert.AlertType.INFORMATION);
                return false;
            }
        } else {

            if (values[8].equals(" ")) {
                values[8] = Utils.formatDateTime(now);
            }

            if ("Current".equals(accountType)) {
                values[1] = String.format("%.2f", currentAmount + amount);
            } else if ("Saving".equals(accountType)) {
                values[2] = String.format("%.2f", savingAmount + amount);

                if ((initialDateStr.equals(" ") && MatureDateStr.equals(" "))) {

                    int timeLimitDays = Integer.parseInt(timeLimit);
                    LocalDateTime expireDate = now.plusDays(timeLimitDays);
                    values[6] = Utils.formatDateTime(now);
                    values[7] = Utils.formatDateTime(expireDate);
                }
            }
        }
        return true;
    }

    /**
     * Logs the transaction details to a CSV file.
     *
     * @param userId the ID of the user
     * @param dateTime the date and time of the transaction
     * @param amount the amount involved in the transaction
     * @param accountType the type of account (current or saving)
     * @param serviceType the type of service (deposit or withdrawal)
     * @param description the description of the transaction
     */
    private void logTransaction(String userId, LocalDateTime dateTime, float amount, String accountType, String serviceType, String description) {
        File transactionFile = new File(Utils.CSV_FILE_PATH_transactionRecord);

        if (!transactionFile.exists()) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(transactionFile))) {
                // Write the headers for the CSV file
                bw.write("ID,Date,Amount,AccountType,ServiceType,Description" + System.lineSeparator());
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error writing the header to the transaction record file.");
                return;
            }
        }

        // Append the transaction details to the file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(transactionFile, true))) {
            bw.write(userId + "," + Utils.formatDateTime(dateTime) + "," + amount + "," + accountType + "," + serviceType + "," + description + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error writing the transaction record.");
        }
    }

    /**
     * Handles the back button click event. Navigates back to the Child_MainPage.fxml page.
     *
     * @param event the action event triggered by clicking the back button
     */
    @FXML
    private void handleBack(ActionEvent event) {
        Utils.showPage("Child_MainPage.fxml", btnBack);
    }
}