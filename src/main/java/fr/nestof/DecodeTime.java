package fr.nestof;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import nu.nethome.util.ps.FieldValue;
import nu.nethome.util.ps.ProtocolDecoderSink;
import nu.nethome.util.ps.ProtocolMessage;

public class DecodeTime {
    private static final int IDLE = 0;

    private static final int READING_MESSAGE = 1;

    private static final int MESSAGE_LENGTH = 84;

    private static final int NB_DATA_BY_MESSAGE = 7;

    private int state = DecodeTime.IDLE;

    private final List<Long> datas = new ArrayList<Long>();

    private long data = 0;

    private int bitCounter = 0;

    private int repeatCount = 0;

    private final ProtocolDecoderSink sink = null;

    private double lastPulse;

    private boolean lastState = false;

    private int pulseIndex = 0;

    private String decodedDataValue = "";

    private String decodedMessageValue = "";

    private String lastDecodedMessageValue = "";

    /**
     * @param args
     * @throws FileNotFoundException
     */
    public static void main(final String[] args) {

        final InputStream is = ClassLoader.getSystemResourceAsStream("time analyse.txt");

        final DecodeTime decodeTime = new DecodeTime();

        BufferedReader reader = null;

        try {

            reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            while (line != null) {
                if (!line.startsWith("--")) {

                    final Double pulse = Double.parseDouble(line.split(" - ")[0]);
                    boolean state = false;
                    if ("true".equals(line.split(" - ")[1])) {
                        state = true;
                    }
                    decodeTime.parse(pulse, state);
                }
                line = reader.readLine();
            }
        } catch (final FileNotFoundException ex) {
            Logger.getLogger(DecodeTime.class.getName()).log(Level.SEVERE, null, ex);
        } catch (final IOException ex) {
            Logger.getLogger(DecodeTime.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (final IOException ex) {
                Logger.getLogger(DecodeTime.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    /**
     * An internal helper function which collects decoded bits and assembles
     * messages
     * 
     * @param b a decoded bit
     */
    private void addBit(final boolean b) {
        final int bit = b ? 1 : 0;

        decodedDataValue = bit + decodedDataValue;
        decodedMessageValue = bit + decodedMessageValue;

        data <<= 1;
        data |= bit;

        bitCounter++;

        // Check if a single data is receive
        if (bitCounter % (DecodeTime.MESSAGE_LENGTH / DecodeTime.NB_DATA_BY_MESSAGE) == 0) {
            log(" DATA : " + Long.toBinaryString(data) + " 0x" + Long.toHexString(data) + " " + data + " | ", false);

            log(decodedDataValue + " 0x" + Long.toHexString(Long.parseLong(decodedDataValue, 2)) + " "
                            + Long.parseLong(decodedDataValue, 2), true);

            datas.add(data);
            data = 0;
        }

        // Check if this is a complete message
        if (bitCounter == DecodeTime.MESSAGE_LENGTH) {

            log("Message : ", false);
            for (final Long data : datas) {
                log(" 0x" + Long.toHexString(data), false);
            }
            log("", true);

            // It is, read the parameters
            final int command = (int ) data & 0xFF;
            final int address = (int ) (data >> 8);

            // Create the message
            final ProtocolMessage message = new ProtocolMessage("Demo", command, address, 2);
            message.setRawMessageByteAt(0, command);
            message.setRawMessageByteAt(1, address);

            message.addField(new FieldValue("Command", command));
            message.addField(new FieldValue("Address", address));

            // Check if this is a repeat
            if (decodedMessageValue.equals(lastDecodedMessageValue)) {
                repeatCount++;
                message.setRepeat(repeatCount);
            } else {
                repeatCount = 0;
            }

            // Report the parsed message
            // TODO sink.parsedMessage(message);
            lastDecodedMessageValue = decodedMessageValue;
            data = 0;
            bitCounter = 0;
            state = DecodeTime.IDLE;
        }

    }

    public int parse(final double pulse, final boolean state) {

        switch (this.state) {
            case IDLE: {
                if (pulse >= 1175 && pulse <= 1315 && lastPulse > 5100 && state) {
                    // Start message pulse
                    pulseIndex = 1;
                    this.state = DecodeTime.READING_MESSAGE;
                    log("Start " + pulse, true);
                    log("h", false);
                    decodedDataValue = "";
                    decodedMessageValue = "";
                    datas.clear();
                } else {
                    log("" + pulse, true);
                }
                break;
            }
            case READING_MESSAGE: {
                if (lastState == state) {
                    // 2 consecutive state must be different
                    break;
                }

                if (pulse >= 340 && pulse <= 612) {
                    // Single pulse
                    if (!state) {
                        log("b", false);
                    } else {
                        log("h", false);
                    }
                    pulseIndex++;
                    if (pulseIndex % 2 == 0) {
                        addBit(!state);
                    }
                } else if (pulse >= 700 && pulse <= 865) {
                    // Double pulse
                    if (!state) {
                        log("bb", false);
                    } else {
                        log("hh", false);
                    }
                    pulseIndex += 2;
                    if (pulseIndex % 2 == 1) {
                        addBit(!state);
                    }
                } else if (pulse >= 1179 && pulse <= 1315 & state) {
                    // Datas separator pulse
                    log("h", false);
                    pulseIndex = 1;
                    data = 0;
                    decodedDataValue = "";
                } else if (pulse > 5100) {
                    // End message pulse
                    log("b", false);
                    addBit(!state);
                    log("End " + pulse, true);
                    pulseIndex = 0;
                    decodedDataValue = "";
                    decodedMessageValue = "";
                    this.state = DecodeTime.IDLE;
                    data = 0;
                    bitCounter = 0;
                } else {
                    log("error" + pulse, true);
                    pulseIndex = 0;
                    decodedDataValue = "";
                    decodedMessageValue = "";
                    this.state = DecodeTime.IDLE;
                    data = 0;
                    bitCounter = 0;
                }
                break;
            }
        }
        lastState = state;
        lastPulse = pulse;
        return this.state;
    }

    public void log(final String line, final boolean newLine) {
        if (newLine) {
            System.out.println(line);
        } else {
            System.out.print(line);
        }
    }
}
