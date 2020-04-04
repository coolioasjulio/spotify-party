package com.coolioasjulio.spotify;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class Client {
    public static void main(String[] args) {
        Client c = new Client();
        c.start();
    }

    private final String version = "v0.1-beta";
    private PartyManager partyManager;
    private final Object managerLock = new Object();
    private Thread monitorThread;
    private ClientGUI gui;

    public Client() {
        Auth.authenticate();
    }

    public void start() {
        gui = new ClientGUI();

        gui.versionLabel.setText(version);
        gui.joinPartyButton.addActionListener(this::joinParty);
        gui.createPartyButton.addActionListener(this::createParty);
        gui.leavePartyButton.addActionListener(this::leaveParty);
        gui.endPartyButton.addActionListener(this::leaveParty);
        gui.logOutButton.addActionListener(this::logout);

        Auth.getAPI().getCurrentUsersProfile().build().executeAsync()
                .thenAccept(u -> gui.usernameLabel.setText("Logged in as: " + u.getDisplayName()));

        JFrame frame = new JFrame("Spotify Party");
        frame.setContentPane(gui.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private void displayError(String error) {
        JOptionPane.showMessageDialog(gui.mainPanel, error, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void monitorTask() {
        while (!Thread.interrupted()) {
            boolean shouldCheck;
            synchronized (managerLock) {
                shouldCheck = partyManager != null;
            }
            try {
                if (shouldCheck) {
                    String line = partyManager.in.readLine();
                    if (line == null) {
                        new Thread(() -> JOptionPane.showMessageDialog(gui.mainPanel,
                                "The party has ended!", "Info", JOptionPane.INFORMATION_MESSAGE)).start();
                        leaveParty(null);
                        break;
                    }
                    if (partyManager.isHost()) {
                        int i = Integer.parseInt(line.strip());
                        gui.numMembersLabel.setText("Members: " + i);
                    }
                }
            } catch (IOException e) {
                break;
            } catch(NullPointerException | NumberFormatException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void setMonitorTaskEnabled(boolean enabled) {
        if (enabled) {
            if (monitorThread == null || !monitorThread.isAlive()) {
                monitorThread = new Thread(this::monitorTask);
                monitorThread.setDaemon(true);
                monitorThread.start();
            }
        } else {
            if (monitorThread != null) {
                monitorThread.interrupt();
            }
        }
    }

    private void disableAllButtons() {
        gui.leavePartyButton.setEnabled(false);
        gui.createPartyButton.setEnabled(false);
        gui.joinPartyButton.setEnabled(false);
        gui.endPartyButton.setEnabled(false);
        gui.logOutButton.setEnabled(false);
    }

    private void disableExcept(JButton button) {
        disableAllButtons();
        button.setEnabled(true);
    }

    private void enableAllButtons() {
        gui.leavePartyButton.setEnabled(true);
        gui.createPartyButton.setEnabled(true);
        gui.joinPartyButton.setEnabled(true);
        gui.endPartyButton.setEnabled(true);
        gui.logOutButton.setEnabled(true);
    }

    private void logout(ActionEvent e) {
        int choice = JOptionPane.showConfirmDialog(gui.mainPanel, "Are you sure you want to log out and exit?", "Log out", JOptionPane.YES_NO_OPTION);
        if (choice == 0) {
            if (Auth.removeCachedToken()) {
                JOptionPane.showMessageDialog(gui.mainPanel,
                        "You have been logged out! The program will now exit.\n" +
                                "Make sure the correct account is logged in on your browser before restarting.");
                System.exit(0);
            } else {
                displayError("There was an error logging out!");
            }
        }
    }

    private void leaveParty(ActionEvent e) {
        enableAllButtons();
        setMonitorTaskEnabled(false);
        synchronized (managerLock) {
            if (partyManager != null) {
                partyManager.close();
                partyManager = null;
            }
        }
        gui.connectionStatusLabel.setText("Not connected");
        gui.numMembersLabel.setText("Not connected");
    }

    private void createParty(ActionEvent e) {
        boolean success = false;
        synchronized (managerLock) {
            if (partyManager == null) {
                partyManager = PartyManager.createParty();
                success = partyManager != null;
                if (success) {
                    gui.joinCodeCreateField.setText(partyManager.getId());
                }
            }
        }
        if (success) {
            setMonitorTaskEnabled(true);
            gui.numMembersLabel.setText("Members: 0");
            disableExcept(gui.endPartyButton);
        } else {
            displayError("There was an error contacting the server!");
        }
    }

    private void joinParty(ActionEvent e) {
        try {
            String id = gui.joinCodeJoinField.getText();
            PartyManager pm = PartyManager.joinParty(id);
            if (pm != null) {
                disableExcept(gui.leavePartyButton);
                synchronized (managerLock) {
                    if (partyManager == null) {
                        partyManager = pm;
                        gui.joinCodeCreateField.setText(partyManager.getId());
                    }
                }
                gui.connectionStatusLabel.setText("Connected!");
                setMonitorTaskEnabled(true);
            } else {
                displayError("Invalid party code!");
            }
        } catch (RuntimeException ex) {
            displayError("There was an error contacting the server!");
        }
    }
}
