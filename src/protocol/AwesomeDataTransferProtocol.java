package protocol;

import client.Utils;

import java.util.ArrayList;
import java.util.Arrays;

public class AwesomeDataTransferProtocol extends IRDTProtocol {

    // change the following as you wish:
    static final int HEADERSIZE = 1;   // number of header bytes in each packet
    static final int DATASIZE = 128;   // max. number of user data bytes in each packet
    private ArrayList<Integer[]> packets;


    public void send(int packetNo) {
        getNetworkLayer().sendPacket(packets.get(packetNo));
        System.out.println("Sent one packet with header=" + packets.get(packetNo)[0]);
    }

    @Override
    public void sender() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Integer[] packet = getNetworkLayer().receivePacket();
                    if (packet != null) {
                        System.out.println("Missing acknowledgement");
                        send(packet[0] - 1);
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();

        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        Integer[] newFile = new Integer[fileContents.length];

        // keep track of where we are in the data
        int filePointer = 0;

        int headerCount = 1;

        packets = new ArrayList<>();

        while(filePointer != fileContents.length) {
            int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + datalen];
            if(filePointer + datalen < fileContents.length) {
                pkt[0] = headerCount;
            } else {
                pkt[0] = 0;
            }
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
            packets.add(pkt);
            //getNetworkLayer().sendPacket(pkt);

            filePointer += datalen;
            headerCount++;
        }
        for (int i = 0; i < packets.size(); i++) {
            send(i);
        }

        // and loop and sleep; you may use this loop to check for incoming acks...
        boolean stop = false;
        while (!stop) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                stop = true;
            }
        }

    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int z = (Integer) tag;
        // handle expiration of the timeout:
        System.out.println("Timer expired with tag=" + z);
    }

    @Override
    public void receiver() {
        System.out.println("Receiving...");

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];

        // loop until we are done receiving the file
        Integer[] packet = getNetworkLayer().receivePacket();
        while (packet == null) {
            try {
                System.out.println("Sleeping...");
                Thread.sleep(10);
                packet = getNetworkLayer().receivePacket();
            } catch (InterruptedException ignored) {

            }
        }
        while (packet != null) {
            // tell the user
            System.out.println("Received packet, length="+packet.length+"  first byte="+packet[0] );

            // append the packet's data part (excluding the header) to the fileContents array, first making it larger
            int oldlength=fileContents.length;
            int datalen= packet.length - HEADERSIZE;
            fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
            System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);

            // and let's just hope the file is now complete
            //stop=true;

            packet = getNetworkLayer().receivePacket();
            if (packet == null) {
                try {
                    Thread.sleep(10);
                    packet = getNetworkLayer().receivePacket();
                } catch (InterruptedException ignored) {
                }
            }
        }

        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }
}
