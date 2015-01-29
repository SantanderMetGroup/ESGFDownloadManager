package es.unican.meteo.esgf.search;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class MetalinkGenerator {

    /** Logger. */
    static private org.slf4j.Logger LOG = org.slf4j.LoggerFactory
            .getLogger(MetalinkGenerator.class);

    /**
     * Generate the Metalink of {@link SearchResponse}
     *
     * @param searchResponse
     * @param outputStream
     * @throws XMLStreamException
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static void exportToMetalink(SearchResponse searchResponse,
            OutputStream outputStream) throws XMLStreamException,
            IllegalArgumentException, IOException {

        if (searchResponse.isCompleted() == false) {
            LOG.error("Search harvesting of {} isn't complete",
                    searchResponse.getName());
            throw new IllegalStateException("Search harvesting of"
                    + searchResponse.getName() + "isn't complete");
        }

        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        Date date = new Date();

        Writer ioWriter = new OutputStreamWriter(outputStream, "UTF-8");
        XMLStreamWriter writer = factory.createXMLStreamWriter(ioWriter);
        // the bug in previous code:
        // XMLStreamWriter xtw = xof.createXMLStreamWriter(new
        // FileWriter(fileName));
        // http://stackoverflow.com/questions/2943605/stax-setting-the-version-and-encoding-using-xmlstreamwriter
        // the default platform encoding (which must be CP-1252, windows?).
        // Always explicitly specify encoding you are using.
        // passing OutputStream and encoding to let XMLStreamWriter do the
        // right thing)

        // write version, type,pubdate, and other attributes
        startMetalink(writer);

        writer.writeStartElement("description");
        String description = "search="
                + searchResponse.getSearch().generateServiceURL();
        writer.writeCharacters(description);
        writer.writeEndElement();

        writer.writeStartElement("files");

        // For each dataset of search response
        for (String datasetInstanceId : searchResponse
                .getDatasetHarvestingStatus().keySet()) {

            // get the dataset of system
            Dataset dataset = searchResponse.getDataset(datasetInstanceId);

            // put to XML only the files that satisfy the constraints
            Set<String> fileInstanceIDs = searchResponse
                    .getMapDatasetFileInstanceID().get(datasetInstanceId);

            for (DatasetFile file : dataset.getFiles()) {
                // only add files that are in set of instance_id of files
                if (fileInstanceIDs.contains(standardizeESGFFileInstanceID(file
                        .getInstanceID()))) {

                    // write file, its resources and checksum
                    writeFileElement(file, dataset, writer);

                }
            }
        }

        writer.writeEndElement();// </files>

        endMetalink(writer);

    }

    /**
     * Generate the Metalink of a set of files of {@link Dataset}
     *
     * @param dataset
     * @param filesToDownload
     *            the instance_id's of the subselection of files that will be
     *            download. These id's must be in standard format (i.e. without
     *            .nc_0, .nc_1, etc instead of .nc)
     * @param outputStream
     * @throws XMLStreamException
     * @throws IOException
     */
    public static void exportToMetalink(Dataset dataset,
            Set<String> filesToDownload, OutputStream outputStream)
            throws XMLStreamException, IOException {

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        Date date = new Date();

        Writer ioWriter = new OutputStreamWriter(outputStream, "UTF-8");
        XMLStreamWriter writer = factory.createXMLStreamWriter(ioWriter);
        // the bug in previous code:
        // XMLStreamWriter xtw = xof.createXMLStreamWriter(new
        // FileWriter(fileName));
        // http://stackoverflow.com/questions/2943605/stax-setting-the-version-and-encoding-using-xmlstreamwriter
        // the default platform encoding (which must be CP-1252, windows?).
        // Always explicitly specify encoding you are using.
        // passing OutputStream and encoding to let XMLStreamWriter do the
        // right thing)

        // write version, type,pubdate, and other attributes
        startMetalink(writer);

        writer.writeStartElement("description");
        String description = "dataset=" + dataset.getInstanceID();
        writer.writeCharacters(description);
        writer.writeEndElement();

        writer.writeStartElement("files");// <files>

        for (DatasetFile file : dataset.getFiles()) {
            if (filesToDownload.contains(standardizeESGFFileInstanceID(file
                    .getInstanceID()))) {
                // write file, its resources and checksum
                writeFileElement(file, dataset, writer);
            }
        }

        writer.writeEndElement();// </files>

        endMetalink(writer);

    }

    /**
     * Generate the Metalink of all files of {@link Dataset}
     *
     * @param dataset
     * @param outputStream
     * @throws XMLStreamException
     * @throws IOException
     */
    public static void exportToMetalink(Dataset dataset,
            OutputStream outputStream) throws XMLStreamException, IOException {

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        Date date = new Date();

        Writer ioWriter = new OutputStreamWriter(outputStream, "UTF-8");
        XMLStreamWriter writer = factory.createXMLStreamWriter(ioWriter);
        // the bug in previous code:
        // XMLStreamWriter xtw = xof.createXMLStreamWriter(new
        // FileWriter(fileName));
        // http://stackoverflow.com/questions/2943605/stax-setting-the-version-and-encoding-using-xmlstreamwriter
        // the default platform encoding (which must be CP-1252, windows?).
        // Always explicitly specify encoding you are using.
        // passing OutputStream and encoding to let XMLStreamWriter do the
        // right thing)

        // write version, type,pubdate, and other attributes
        startMetalink(writer);

        writer.writeStartElement("description");
        String description = "dataset=" + dataset.getInstanceID();
        writer.writeCharacters(description);
        writer.writeEndElement();

        writer.writeStartElement("files");// <files>

        for (DatasetFile file : dataset.getFiles()) {
            // write file, its resources and checksum
            writeFileElement(file, dataset, writer);
        }

        writer.writeEndElement();// </files>

        endMetalink(writer);

    }

    /**
     * Write file element with its resources and checksum in Metalink
     *
     * @param file
     * @param dataset
     *            Dataset that the file belogs
     * @param writer
     *            XML Stream writer
     * @throws XMLStreamException
     */
    private static void writeFileElement(DatasetFile file, Dataset dataset,
            XMLStreamWriter writer) throws XMLStreamException {
        if (file.hasService(Service.HTTPSERVER)
                || file.hasService(Service.OPENDAP)
                || file.hasService(Service.GRIDFTP)) {
            writer.writeStartElement("file");

            String var = "";

            // name attribute
            if (file.contains(Metadata.TITLE)) {
                var = file.getMetadata(Metadata.TITLE).toString().split("_")[0];
                writer.writeAttribute("name",
                        dataset.getInstanceID() + File.separator
                        + (String) file.getMetadata(Metadata.TITLE));
            } else {
                String datasetId = file.getDatasetInstanceID();
                String fileId = file.getInstanceID();
                // text = fileId-datasetId + size
                // sum 1 to erase the dot
                var = fileId.substring(datasetId.length() + 1).split("_")[0];
                writer.writeAttribute(
                        "name",
                        dataset.getInstanceID() + File.separator
                        + fileId.substring(datasetId.length() + 1));
            }

            // Add identity
            if (file.contains(Metadata.MASTER_ID)) {
                writer.writeStartElement("identity");
                writer.writeCharacters((String) file
                        .getMetadata(Metadata.MASTER_ID));
                writer.writeEndElement();// </identity>
            }

            // Add version (dataset better)
            if (dataset.contains(Metadata.VERSION)) {
                writer.writeStartElement("version");
                writer.writeCharacters((String) dataset
                        .getMetadata(Metadata.VERSION));
                writer.writeEndElement();// </version>
            }

            // Add file size
            if (file.contains(Metadata.SIZE)) {
                writer.writeStartElement("size");
                String size = ((Long) file.getMetadata(Metadata.SIZE))
                        .toString();
                writer.writeCharacters(size);
                writer.writeEndElement();// </size>
            }

            // Add mimetype
            writer.writeStartElement("mimetype");
            writer.writeCharacters("application/netcdf");
            writer.writeEndElement();// </mimetype>

            // Add tags
            writer.writeStartElement("tags");
            String tags = generateTagsOf(dataset, var);
            writer.writeCharacters(tags);
            writer.writeEndElement();// </tags>

            // Add verification
            if (file.contains(Metadata.CHECKSUM)
                    && file.contains(Metadata.CHECKSUM_TYPE)) {

                String checksumType = ((String) file
                        .getMetadata(Metadata.CHECKSUM_TYPE)).toLowerCase();
                String checksum = file.getMetadata(Metadata.CHECKSUM);

                writer.writeStartElement("verification");
                writer.writeStartElement("hash");
                writer.writeAttribute("type", checksumType);
                writer.writeCharacters(checksum);
                writer.writeEndElement();// </hash>
                writer.writeEndElement();// </verification>
            }

            // Add resources of file
            writer.writeStartElement("resources");

            for (RecordReplica fileReplica : file
                    .getReplicasOfService(Service.HTTPSERVER)) {
                writer.writeStartElement("url");
                writer.writeAttribute("type", "http");
                writer.writeCharacters(fileReplica
                        .getUrlEndPointOfService(Service.HTTPSERVER));
                writer.writeEndElement();// </url>
            }

            if (file.hasService(Service.OPENDAP)) {
                for (RecordReplica fileReplica : file
                        .getReplicasOfService(Service.OPENDAP)) {
                    writer.writeStartElement("url");
                    writer.writeAttribute("type", "opendap");

                    String opendapURL = fileReplica
                            .getUrlEndPointOfService(Service.OPENDAP);
                    opendapURL = opendapURL.replaceFirst("http", "dods");
                    opendapURL = opendapURL.substring(0,
                            opendapURL.lastIndexOf(".html"));
                    writer.writeCharacters(opendapURL);

                    writer.writeEndElement();// </url>
                }
            }

            if (file.hasService(Service.GRIDFTP)) {
                for (RecordReplica fileReplica : file
                        .getReplicasOfService(Service.GRIDFTP)) {
                    writer.writeStartElement("url");
                    writer.writeAttribute("type", "gridftp");
                    writer.writeCharacters(fileReplica
                            .getUrlEndPointOfService(Service.GRIDFTP));
                    writer.writeEndElement();// </url>
                }
            }

            writer.writeEndElement();// </resources>
            writer.writeEndElement();// </file>

        } else {
            LOG.warn("File {} hasn't HTTP service or OPeNDAP or GridFTP",
                    file.getMetadata(Metadata.TITLE));
        }
    }

    /**
     * Generate metalink tags for a dataset
     *
     * @param dataset
     * @return
     */
    private static String generateTagsOf(Dataset dataset, String var) {

        String tags = var;

        if (dataset.contains(Metadata.TITLE)) {
            tags = "variable=" + tags + ", "
                    + dataset.getMetadata(Metadata.TITLE);
        }

        return tags;
    }

    /**
     * Write version, type,pubdate, and other attributes
     *
     * @param writer
     * @throws XMLStreamException
     */
    private static void startMetalink(XMLStreamWriter writer)
            throws XMLStreamException {

        Date date = new Date();
        writer.writeStartDocument("UTF-8", "1.0");

        writer.writeStartElement("metalink"); // <metalink>
        writer.writeAttribute("version", "3.0");
        writer.writeAttribute("xmlns", "http://www.metalinker.org/");
        writer.writeAttribute("type", "static");
        writer.writeAttribute("pubdate", date.toGMTString());
        writer.writeAttribute("generator",
                "https://meteo.unican.es/trac/wiki/ESGFToolsUI");
    }

    /**
     * End metalink
     *
     * @param writer
     * @throws XMLStreamException
     */
    private static void endMetalink(XMLStreamWriter writer)
            throws XMLStreamException {
        writer.writeEndElement(); // </metalink>
        writer.writeEndDocument();

        writer.flush();
        writer.close();
    }

    /**
     * Verify if instance ID of ESGF file is correct and if id is corrupted then
     * it corrects the id
     *
     * @param instanceID
     *            instance_id of file
     * @return the same instance_id if it is a valid id or a new corrected
     *         instance_id , otherwise
     */
    private static String standardizeESGFFileInstanceID(String instanceID) {
        // file instane id have this form
        //
        // project.output.model[...]_2000010106-2006010100.nc
        // dataset id have this form
        //
        // project.output.model[...]_2000010106-2006010100

        // If id have ".nc_0" or others instead of .nc
        // Then warning and return correct id

        if (instanceID.matches(".*\\.nc_\\d$")) {
            String[] splitted = instanceID.split(".nc_\\d$");
            instanceID = splitted[0] + ".nc";
        }

        return instanceID;
    }
}
