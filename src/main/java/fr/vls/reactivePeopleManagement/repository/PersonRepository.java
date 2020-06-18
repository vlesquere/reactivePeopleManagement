package fr.vls.reactivePeopleManagement.repository;

import fr.vls.reactivePeopleManagement.model.Person;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PersonRepository extends ReactiveCrudRepository<Person, Long> {
    Flux<Person> findByLastname(String lastname);
}
