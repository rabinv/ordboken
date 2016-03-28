var setStar = function(on) {
    var el = document.getElementById("bookmark");
    var className = on ? "on" : "off";

    el.onclick = function() {
        ordboken.toggleStar();
        return false;
    };

    el.className = className;
};

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

var createListEl = function(head, cls) {
	// Wrap text nodes so that they get selected with e.g. nextAll()
	head.parent().contents().filter(function(){
		return this.nodeType === 3;
	}).wrap('<span/>');

	var regex = /^ *[a-z]\)$/
	var items = head.nextAll('b').filter(function() {
		return regex.test($(this).text());
	});

	var list = $('<ul class="' + cls + '">');
	var listitems = [];

	var start = head;
	for (var i = 0; i < items.length; i++) {
		var end = i + 1 == items.length ? 'br' : items[i + 1];

		start = start.nextUntil(end)
			.wrapAll('<li>')
			.parent();
		listitems.push(start);
	}

	for (var i = 0; i < listitems.length; i++) {
		list.append(listitems[i]);
	}
	head.after(list);
};

var createList = function(title, cls) {
	$("span:contains('" + title + "')").each(function() {
		createListEl($(this), cls);
	});
};

var createLists = function() {
	createList('BET.NYANSER:', 'nyanser');
};

var expandTildes = function() {
    if ($('p').not('.headword').not('.rel').length == 1) {
        // The check below to exclude the declinations paragraph
        // assumes that the declinations are in a separate paragraph,
        // but that's not the case for words with only one meaning.
        // So for those words, we insert a dummy paragraph at • (which
        // starts the defintion).
        var html = $('body').html();

        if (html.indexOf('•') == -1) {
            return;
        }

        $('body').html(html.replace('•', '</p><p class="dummy">•'));
    }

    // Don't include the declinations since they may contain several spelling
    // variants and the ~ there refers to the current spelling variant and
    // not the headword.  See "röse" for an example.
    $('p').not('.headword').not(':first').find("i:contains('~')").each(function() {
        $(this).html($(this).html().replace(/~/g, document.title));
    });
}

$(function() {
    expandTildes();
    createLists();

    var ps = document.getElementsByTagName("p");
    for (var i = 0; i < ps.length; i++) {
        createLinks(ps[i]);
    }

    ordboken.loaded();
});
