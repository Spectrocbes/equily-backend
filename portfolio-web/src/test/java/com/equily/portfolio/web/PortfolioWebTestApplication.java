package com.equily.portfolio.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// Minimal bootstrap so @WebMvcTest can locate controllers and advisors in
// com.equily.portfolio.web.*
// when the bootstrap module is not on the test classpath.
@SpringBootApplication
class PortfolioWebTestApplication {}
