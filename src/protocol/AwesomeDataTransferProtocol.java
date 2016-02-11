package protocol;

import client.Utils;

import java.util.Arrays;

public class AwesomeDataTransferProtocol extends IRDTProtocol {

    // change the following as you wish:
    static final int HEADERSIZE = 1;   // number of header bytes in each packet
    static final int DATASIZE = 128;   // max. number of user data bytes in each packet

    @Override
    public void sender() {
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        // keep track of where we are in the data
        int filePointer = 0;

        int headerCount = 0;

        while(filePointer != fileContents.length) {
            int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + datalen];
            pkt[0] = headerCount;
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
            getNetworkLayer().sendPacket(pkt);
            System.out.println("Sent one packet with header=" + pkt[0]);
            filePointer += datalen;
            headerCount++;
        }

        //Utils.Timeout.SetTimeout(1000, this, 28);



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
