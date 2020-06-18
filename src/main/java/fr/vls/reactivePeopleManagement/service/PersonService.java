package fr.vls.reactivePeopleManagement.service;

import fr.vls.reactivePeopleManagement.model.Person;
import fr.vls.reactivePeopleManagement.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class PersonService {

    @Autowired
    PersonRepository personRepository;

    public Mono<Long> save(Mono<Person> person) {
        return person.flatMap(p -> personRepository.save(p).map(Person::getId));
    }

    public Flux<Person> saveAll(Flux persons) {
        Flux<Person> results = personRepository.saveAll(persons);
        results.subscribe();
        return results;
    }

    public Flux<Person> findAll() {
        return personRepository.findAll();
    }

    public Flux<Person> findByLastName(String lastname) {
        return personRepository.findByLastname(lastname);
    }

    public Mono<Void> deleteAll() {
        personRepository.deleteAll().subscribe();
        return Mono.empty();
    }

    public Mono<Void> deleteById(String id) {
        return personRepository.deleteById(Long.valueOf(id));
    }

    public Mono<Person> getById(String id) {
        return personRepository.findById(Long.valueOf(id));
    }
}
