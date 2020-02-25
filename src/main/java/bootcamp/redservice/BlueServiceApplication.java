package bootcamp.redservice;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class BlueServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlueServiceApplication.class, args);
	}

}

@RestController
@Slf4j
class RedServiceController {
	@Autowired DiscoveryClient discoveryClient;
	@Autowired WebClient.Builder webClientBuilder;
	@Value("${random.value}") String marker;
	
	@GetMapping("/instances")
	public List<?> getInstanceIds() {
		log.info("Marker is {}", marker);
		return discoveryClient.getServices()
			.stream()
			.map( serviceId -> discoveryClient.getInstances(serviceId))
			.collect(Collectors.toList());
	}
	
	@GetMapping("/proxied-instances") 
	public Mono<List> getProxiedInstanceIds() {
		return webClientBuilder.build().get()
			.uri("http://red-service/instances")
			.retrieve()
			.bodyToMono(List.class);
	}
	
	@GetMapping(path="/votes",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@CircuitBreaker(name = "default", fallbackMethod = "getEmptyVote")
	public Flux<Vote> getVotes() {
		return webClientBuilder.build().get()
				.uri("http://vote-generator/random-numbers")
				.retrieve()
				.bodyToFlux(Integer.class)
				.map(i -> new Vote(i, "", marker));
	}
	
	@GetMapping("/empty-vote")
	public Flux<Vote> getEmptyVote(Exception ex) {
		return Flux.just(new Vote(0, "No data available for Red service", marker));
	}
	
	
}

@Data
@AllArgsConstructor
class Vote {
	Integer count;
	String unavailable;
	String district;
}

@Configuration
class Config {
	@Bean @LoadBalanced
	WebClient.Builder webClientBuilder(){
		return WebClient.builder();
	}
}
