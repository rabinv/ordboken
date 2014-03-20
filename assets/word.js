var getPos = function(regex, html) {
	var starts = [];

	while (match = regex.exec(html)) {
		starts.push(match.index);
	}

	return starts;
};

var createLinks = function(el) {
	var regex = /([ >/"'(])([A-Za-z\u0080-\u00FF-]+)(?![^<]*>)/g;
	var html = el.innerHTML;
	var astarts = getPos(/<a/gi, html);
	var aends = getPos(/\/a>/gi, html);
	var apos = 0;

	el.innerHTML = html.replace(regex, function(m, before, a, offset) {
		if (a.length <= 2) {
			return before + a;
		}

		while (apos < aends.length && offset > aends[apos]) {
			apos++;
		}

		if (apos < aends.length && offset > astarts[apos] && offset < aends[apos]) {
			return before + a;
		}

		return before + '<a class="normal" href="/search/' + a + '">' + a + "</a>";
	});
};

var ps = document.getElementsByTagName("p");
for (var i = 0; i < ps.length; i++) {
	createLinks(ps[i]);
}
