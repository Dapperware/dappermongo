// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Getting Started",
      "url": "/dappermongo/docs/getting-started/",
      "content": "Getting started You’ll need to first make sure that you have access to a mongo instance or cluster. There are a few ways to do this: Install mongo on your local machine or get a hosted version of mongo through Atlas. You can find more information on that here. Alternatively, if you want to develop locally you can also use docker to run a mongo instance. See the examples project for reference. Finally, you can use the mongodb-atlas CLI tool to create a cluster either locally or on atlas. Installation libraryDependencies += \"com.github.dapperware\" %% \"dappermongo-core\" % \"0.0.1\" Setting up the client After adding the above dependency you can start a MongoClient import dappermongo._ // ZLayer MongoClient.local // Uses the default local settings MongoClient.configured // By default, it looks for a config key of \"mongodb\" MongoClient.configured(NonEmptyChunk(\"mongo\", \"database\")) // You can also specify the config key MongoClient.live // Accepts `MongoSettings` as an environmental parameter // ZIO MongoClient.scoped // Returns a ZIO that can be configured MongoClient.fromSettings(settings) // Accepts settings passed as an argument Example import dappermongo._ import zio._ object Example1 extends ZIOAppDefault { val program = for { db &lt;- MongoClient.database(\"test\") _ &lt;- db.diagnostics.ping } yield () val run = program.provide( MongoClient.local ) }"
    } ,    
    {
      "title": "Home",
      "url": "/dappermongo/",
      "content": ""
    } ,    
    {
      "title": "Intro",
      "url": "/dappermongo/docs/",
      "content": "Introduction Welcome to DapperMongo, a ZIO-friendly MongoDB client. This project is designed to provide an easy-to-use, efficient, and reliable interface for interacting with MongoDB databases in Scala. Why DapperMongo? You might be wondering, why another wrapper library for MongoDB in Scala? The answer is simple: DapperMongo takes the best from each library and combines them into a single “dapper” library. Here are some of the reasons why you should consider using DapperMongo: Ease of Use: DapperMongo is designed with simplicity in mind. It provides a straightforward and intuitive API that makes it easy to perform common database operations. Efficiency: By leveraging the power of ZIO and other high-performance libraries, DapperMongo ensures that your database operations are as efficient as possible. Reliability: DapperMongo is built on top of reliable libraries and uses ZIO’s powerful error handling capabilities to ensure that your database operations are safe and reliable. Best of All Worlds: DapperMongo takes the best features from various MongoDB libraries and combines them into a single, easy-to-use package. This means you get the benefits of all these libraries without having to deal with their individual quirks and complexities. So, if you’re looking for a MongoDB library that’s easy to use, efficient, reliable, and takes the best from each library, look no further than DapperMongo. We hope you’ll find it to be a valuable tool in your Scala development toolkit."
    } ,        
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
