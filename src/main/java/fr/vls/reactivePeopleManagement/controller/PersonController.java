package fr.vls.reactivePeopleManagement.controller;

import fr.vls.reactivePeopleManagement.model.Person;
import fr.vls.reactivePeopleManagement.service.PersonService;
import io.micrometer.core.instrument.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.AsynchronousFileChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/persons")
public class PersonController {

    private final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

    private final SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy", Locale.FRANCE);

    @Autowired
    PersonService personService;

    @PostMapping
    public Mono<Long> create(@RequestBody Mono<Person> person) {
        return personService.save(person);
    }

    @GetMapping
    public Flux<Person> getAll(@RequestParam(name = "lastname", required = false) String lastname) {
        if (StringUtils.isEmpty(lastname))
            return personService.findAll();
        else
            return personService.findByLastName(lastname);
    }

    @GetMapping("/{id}")
    public Mono<Person> getById(@PathVariable("id") String id) {
        return personService.getById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteById(@PathVariable("id") String id) {
        personService.deleteById(id);
        return "deletion done";
    }

    @DeleteMapping
    public String deleteAll() {
        personService.deleteAll();
        return "deletion done";
    }

    @PostMapping(value = "/upload")
    public Mono<String> handleFileUpload(@RequestPart("file") MultipartFile file) {

        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy", Locale.FRANCE);
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            buffer.lines().forEach(line -> {
                String[] details = line.split(";");
                Person p = new Person();
                p.setEmail(details[0]);
                p.setFirstname(details[1]);
                p.setLastname(details[2]);
                try {
                    p.setBirthdate(formatter.parse(details[3]));
                } catch (ParseException e) {
                    LOGGER.error(e.getMessage(), e);
                }
                personService.save(Mono.just(p));
            });
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return Mono.just("file successfully uploaded");
    }


    @RequestMapping(value="upload-flux", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<String> uploadHandler(@RequestBody Flux<Part> parts) {
        return parts
                .filter(part -> part instanceof FilePart) // only retain file parts
                .ofType(FilePart.class) // convert the flux to FilePart
                .flatMap(this::saveFile); // save each file and flatmap it to a flux of results
    }

    /**
     * tske a {@link FilePart}, transfer it to disk using {@link AsynchronousFileChannel}s and return a {@link Mono} representing the result
     *
     * @param filePart - the request part containing the file to be saved
     * @return a {@link Mono} representing the result of the operation
     */
    private Mono<String> saveFile(FilePart filePart) {
        StringBuilder fileContent = new StringBuilder();

        LOGGER.info("handling file upload {}", filePart.filename());

        // FilePart.content produces a flux of data buffers, each need to be written to the file
        return filePart.content().doOnEach(dataBufferSignal -> {

            if (dataBufferSignal.hasValue()) {
                // read data from the incoming data buffer into a file array
                DataBuffer dataBuffer = dataBufferSignal.get();
                fileContent.append(IOUtils.toString(dataBuffer.asInputStream()));
            }
        }).doOnComplete(() -> {
            // all done, close the file channel
            LOGGER.info("done processing file parts");

            List<String> lines = new ArrayList<>(Arrays.asList(fileContent.toString().split("\n")));
            List<Person> persons = lines.stream().map(this::createPerson).collect(Collectors.toList());

            personService.saveAll(Flux.fromIterable(persons));

        }).doOnError(t -> {
            // ooops there was an error
            LOGGER.info("error processing file parts");
            // take last, map to a status string
        }).last().map(dataBuffer -> filePart.filename());

    }

    private Person createPerson(String line) {
        String[] details = line.split(";");
        Person p = new Person();
        p.setEmail(details[0]);
        p.setFirstname(details[1]);
        p.setLastname(details[2]);
        try {
            p.setBirthdate(formatter.parse(details[3]));
        } catch (ParseException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return p;
    }
}
