package JobShop;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;

public class JobShopTest {
    public static String generateJobShopInstance(int n, int m) throws IOException {
        Random random = new Random();
        StringBuilder jobOperations = new StringBuilder();
        jobOperations.append(n).append(" ").append(m).append("\n");
        for (int job = 0; job < n; job++) {
            HashSet<Integer> machines = new HashSet<>();
            for (int operation = 0; operation < m; operation++) {
                int machineIndex = random.nextInt(m);// Random machine index [0, m-1]
                while (machines.contains(machineIndex)) {
                    machineIndex = random.nextInt(m);
                }
                machines.add(machineIndex);
                int duration = random.nextInt(15) + 1; // Duration between 1 and 10

                jobOperations.append(machineIndex).append(" ").append(duration).append(" ");
            }
            jobOperations.append("\n");


        }
        return jobOperations.toString().trim();
    }

    public static Path writeInstanceToTempFile(int n, int m, int i) throws IOException {
        String content = generateJobShopInstance(n, m);
        String p = "jobshop_instance_" + n + "_" + m + "_" + i + ".txt";

        Path tempFile = Files.createTempFile("jobshop_instance_" + n + "_" + m + "_" + i, ".txt");
        Files.writeString(tempFile, content);
        return tempFile;
    }

    public static Path writeTestInstance(int n, int m, int i) throws IOException {
        String content = generateJobShopInstance(n, m);

        Path tempFile = Files.createFile(Path.of("data/jobshop/testerror/jobshop_instance_" + n + "_" + m + "_" + i + ".txt"));
        Files.writeString(tempFile, content);
        return tempFile;
    }


    @Test
    public void testOpenShop_test() throws IOException {

        for (int n = 3; n <= 4; n++) {
            for(int m = 0; m < 3; m++) {
                for (int i = 1; i < 3; i++) {
                    Path tempFile = Path.of("data/OPENSHOP/j" + n +"-per"+(m*10)+ "-" + i + ".txt");
                    System.out.println("data/OPENSHOP/j" + n +"per"+(m*10)+ "-" + i + ".txt");
                    int makespanCP = OSCP.main(tempFile.toString());
                    int makespanACS = (int) (JSMain.main(tempFile.toString(), "ACS", true));
                    int makespanAstar = (int) (JSMain.main(tempFile.toString(), "Astar", true));
                    Assert.assertEquals(makespanCP, makespanACS);
                    Assert.assertEquals(makespanCP, makespanAstar);
                }
            }
        }
    }

    @Test
    public void testOpenShop_test2() throws IOException {
        Path tempFile = Path.of("data/OPENSHOP/j" + 4 +"-per"+(2*10)+ "-" + 0 + ".txt");
        System.out.println("data/OPENSHOP/j" + 4 +"per"+(2*10)+ "-" + 0 + ".txt");
        int makespanCP = OSCP.main(tempFile.toString());
        int makespanACS = (int) (JSMain.main(tempFile.toString(), "ACS", true));
        int makespanAstar = (int) (JSMain.main(tempFile.toString(), "Astar", true));
        Assert.assertEquals(makespanCP, makespanACS);
        Assert.assertEquals(makespanCP, makespanAstar);
    }



        @Test
    public void testJobShop_test() throws IOException {
        for (int n = 2; n <= 5; n++) {
            for (int m = 2; m <= 5; m++) {
                for (int i = 1; i < 101; i++) {
                    Path tempFile = Path.of("data/bigTest/jobshop_instance_" + n + "_" + m + "_" + i + ".txt");
                    System.out.println("data/bigTest/jobshop_instance_" + n + "_" + m + "_" + i + ".txt ");
                    int makespanCP = JSCP.main(tempFile.toString());
                    int makespanACS = (int) (JSMain.main(tempFile.toString(),"ACS",false));
                    int makespanAstar = (int) (JSMain.main(tempFile.toString(),"Astar",false));
                    int makespanSOA = (int) (JSMain.main(tempFile.toString(),"SOA",false));
                    Assert.assertEquals(makespanCP, makespanACS);
                    Assert.assertEquals(makespanCP, makespanAstar);
                    Assert.assertEquals(makespanCP, makespanSOA);
                }
            }
        }
    }

    @Test
    public void testJobShop_2_2() throws IOException {
        for (int i = 1; i < 1001; i++) {
            Path tempFile = writeInstanceToTempFile(2, 2, i);
            int makespanCP =  JSCP.main(tempFile.toString());
            int makespanACS = (int) (JSMain.main(tempFile.toString(),"ACS",false));
            int makespanAstar = (int) (JSMain.main(tempFile.toString(),"Astar",false));
            int makespanSOA = (int) (JSMain.main(tempFile.toString(),"SOA",false));
            Assert.assertEquals(makespanCP, makespanACS);
            Assert.assertEquals(makespanCP, makespanAstar);
            Assert.assertEquals(makespanCP, makespanSOA);
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testJobShop_3_3() throws IOException {
        for (int i = 1; i < 1001; i++) {
            Path tempFile = writeInstanceToTempFile(3, 3, i);
            try {
                int makespanCP = JSCP.main(tempFile.toString());
                int makespanACS = (int) JSMain.main(tempFile.toString(), "ACS", false);
                int makespanAstar = (int) JSMain.main(tempFile.toString(), "Astar", false);
                int makespanSOA = (int) JSMain.main(tempFile.toString(), "SOA", false);

                Assert.assertEquals(makespanCP, makespanACS);
                Assert.assertEquals(makespanCP, makespanAstar);
                Assert.assertEquals(makespanCP, makespanSOA);

            } catch (AssertionError e) {
                // Print file content when a failure happens
                System.err.println("Test failed for instance " + i);
                System.err.println("File: " + tempFile);
                System.err.println(Files.readString(tempFile));
                throw e; // rethrow so the test still fails
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Test
    public void testJobShop_4_4() throws IOException {
        for (int i = 1; i < 1001; i++) {
            Path tempFile = writeInstanceToTempFile(4, 4, i);
            try {
                int makespanCP = JSCP.main(tempFile.toString());
                int makespanACS = (int) JSMain.main(tempFile.toString(), "ACS", false);
                int makespanAstar = (int) JSMain.main(tempFile.toString(), "Astar", false);
                int makespanSOA = (int) JSMain.main(tempFile.toString(), "SOA", false);

                Assert.assertEquals(makespanCP, makespanACS);
                Assert.assertEquals(makespanCP, makespanAstar);
                Assert.assertEquals(makespanCP, makespanSOA);

            } catch (AssertionError e) {
                // Print file content when a failure happens
                System.err.println("Test failed for instance " + i);
                System.err.println("File: " + tempFile);
                System.err.println(Files.readString(tempFile));
                throw e; // rethrow so the test still fails
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
//
//    @Test
//    public void testJobShop_5_5() throws IOException {
//        for (int i = 1; i < 101; i++) {
//            Path tempFile = writeInstanceToTempFile(5, 5, i);
//            int makespanCP =  JSCP.main(tempFile.toString());
//            int makespanACS = (int) (-1*JSMain.main(tempFile.toString(),"ACS"));
//            int makespanAstar = (int) (-1*JSMain.main(tempFile.toString(),"Astar"));
//            Assert.assertEquals(makespanCP, makespanACS);
//            Assert.assertEquals(makespanCP, makespanAstar);
//            Files.deleteIfExists(tempFile);
//        }
//    }
//
//    @Test
//    public void testJobShop_6_6() throws IOException {
//        for (int i = 1; i < 20; i++) {
//            Path tempFile = writeInstanceToTempFile(6, 6, i);
//            int makespanCP =  JSCP.main(tempFile.toString());
//            int makespanACS = (int) (-1*JSMain.main(tempFile.toString(),"ACS"));
//            int makespanAstar = (int) (-1*JSMain.main(tempFile.toString(),"Astar"));
//            Assert.assertEquals(makespanCP, makespanACS);
//            Assert.assertEquals(makespanCP, makespanAstar);
//            Files.deleteIfExists(tempFile);
//        }
//    }
}
