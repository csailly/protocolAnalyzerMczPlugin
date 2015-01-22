package fr.nestof;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DecodeHand {

    static String decode(final String toDecode) {
        String decode = "";

        for (int i = 0; i < toDecode.length();) {
            final String bit = toDecode.substring(i, i + 2);
            i += 2;
            switch (bit) {
                case "hb":
                    decode = "1" + decode;
                    break;
                case "bh":
                    decode = "0" + decode;
                    break;
                default:
                    break;
            }
        }
        return decode;
    }

    /**
     * @param args
     * @throws FileNotFoundException
     */
    public static void main(final String[] args) {
        // reading file line by line in Java using BufferedReader
        FileOutputStream hexFos = null;
        BufferedWriter hexWriter = null;

        try {
            hexFos = new FileOutputStream("decode.txt");
            hexWriter = new BufferedWriter(new OutputStreamWriter(hexFos));
        } catch (final FileNotFoundException e) {
            Logger.getLogger(DecodeHand.class.getName()).log(Level.SEVERE, null, e);
            return;
        }

        int i = 1;
        InputStream is = ClassLoader.getSystemResourceAsStream("1.txt");

        BufferedReader reader = null;

        while (is != null) {
            try {
                reader = new BufferedReader(new InputStreamReader(is));
                String line = reader.readLine();
                while (line != null) {
                    if (line.startsWith("--")) {

                    } else {
                        final String decodedValue = DecodeHand.decode(line);

                        final String binaryValue = decodedValue;
                        final Long longValue = Long.parseLong(decodedValue, 2);
                        final String hexValue = "0x" + Long.toHexString(longValue);

                        System.out.println(" " + binaryValue + " - " + longValue + " - " + hexValue);
                        hexWriter.write(binaryValue + " - " + longValue + " - " + hexValue);
                    }
                    line = reader.readLine();
                }
                System.out.print(" -- " + i);
                System.out.println();
                hexWriter.write(" -- " + i + "\n");
            } catch (final FileNotFoundException ex) {
                Logger.getLogger(DecodeHand.class.getName()).log(Level.SEVERE, null, ex);
            } catch (final IOException ex) {
                Logger.getLogger(DecodeHand.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    reader.close();
                } catch (final IOException ex) {
                    Logger.getLogger(DecodeHand.class.getName()).log(Level.SEVERE, null, ex);
                }
                i++;
                is = ClassLoader.getSystemResourceAsStream(i + ".txt");
            }
        }

        try {
            hexWriter.close();
            hexFos.close();
        } catch (final IOException ex) {
            Logger.getLogger(DecodeHand.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
