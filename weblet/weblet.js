var express = require('express');
var request = require('request');

var app = express();

var config = require('./config.json');

Object.keys(config.proxy).forEach(function(prefix) {
  var serverHost = config.proxy[prefix];
  var proxyFrom = '/api/'+prefix, proxyTo = serverHost+'/'+prefix
  console.log('Proxy: '+proxyFrom+' -> '+proxyTo);

  app.use(proxyFrom, function(req, res) {
    var url = proxyTo+(req.url == '/' ? '' : req.url.startsWith('/?') ? req.url.substring(1) : req.url);
    if (config.log_requests) logRequest(req, url, proxyFrom);
    req.pipe(request(url)).pipe(res);
  });
});

var logRequest = function(req, url, proxyFrom) {
  console.log(req.method+' '+proxyFrom+' '+req.url+' -> '+url);
}

app.use(express.static('public'));

app.listen(config.port, function() {
  console.log('Bound weblet to port '+config.port);
});
